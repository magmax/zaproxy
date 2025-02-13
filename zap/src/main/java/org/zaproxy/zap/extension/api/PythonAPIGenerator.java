/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2012 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.api;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Year;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class PythonAPIGenerator extends AbstractAPIGenerator {

    /** Default output directory in zap-api-python project. */
    private static final String DEFAULT_OUTPUT_DIR = "../zap-api-python/src/zapv2/";

    private final String HEADER =
            "# Zed Attack Proxy (ZAP) and its related class files.\n"
                    + "#\n"
                    + "# ZAP is an HTTP/HTTPS proxy for assessing web application security.\n"
                    + "#\n"
                    + "# Copyright "
                    + Year.now()
                    + " the ZAP development team\n"
                    + "#\n"
                    + "# Licensed under the Apache License, Version 2.0 (the \"License\");\n"
                    + "# you may not use this file except in compliance with the License.\n"
                    + "# You may obtain a copy of the License at\n"
                    + "#\n"
                    + "#   http://www.apache.org/licenses/LICENSE-2.0\n"
                    + "#\n"
                    + "# Unless required by applicable law or agreed to in writing, software\n"
                    + "# distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                    + "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                    + "# See the License for the specific language governing permissions and\n"
                    + "# limitations under the License.\n"
                    + "\"\"\"\n"
                    + "This file was automatically generated.\n"
                    + "\"\"\"\n\n";

    /** Map any names which are reserved in python to something legal */
    private static final Map<String, String> nameMap;

    static {
        Map<String, String> initMap = new HashMap<>();
        initMap.put("break", "brk");
        initMap.put("continue", "cont");
        nameMap = Collections.unmodifiableMap(initMap);
    }

    public PythonAPIGenerator() {
        super(DEFAULT_OUTPUT_DIR);
    }

    public PythonAPIGenerator(String path, boolean optional) {
        super(path, optional);
    }

    public PythonAPIGenerator(String path, boolean optional, ResourceBundle resourceBundle) {
        super(path, optional, resourceBundle);
    }

    private void generatePythonElement(
            ApiElement element, String component, String type, Writer out) throws IOException {

        out.write("\n\n");
        boolean hasParams = !element.getParameters().isEmpty();

        if (!hasParams && type.equals(VIEW_ENDPOINT)) {
            out.write("    @property\n");
        }
        out.write("    def " + createFunctionName(element.getName()) + "(self");

        for (ApiParameter parameter : element.getParameters()) {
            out.write(", " + parameter.getName().toLowerCase(Locale.ROOT));
            if (!parameter.isRequired()) {
                out.write("=None");
            }
        }

        if (type.equals(ACTION_ENDPOINT) || type.equals(OTHER_ENDPOINT)) {
            // Always add the API key - we've no way of knowing if it will be required or not
            out.write(", " + API.API_KEY_PARAM + "=''");
            hasParams = true;
        }

        out.write("):\n");

        // Add description if defined
        String descTag = element.getDescriptionTag();
        try {
            String desc = getMessages().getString(descTag);
            out.write("        \"\"\"\n");
            out.write("        " + desc + "\n");
            if (isOptional()) {
                out.write("        " + OPTIONAL_MESSAGE + "\n");
            }
            out.write("        \"\"\"\n");
        } catch (Exception e) {
            // Might not be set, so just print out the ones that are missing
            System.out.println("No i18n for: " + descTag);
            if (isOptional()) {
                out.write("        \"\"\"\n");
                out.write("        " + OPTIONAL_MESSAGE + "\n");
                out.write("        \"\"\"\n");
            }
        }

        String method = "_request";
        String baseUrl = "base";
        if (type.equals(OTHER_ENDPOINT)) {
            method += "_other";
            baseUrl += "_other";
        }

        StringBuilder reqParams = new StringBuilder();
        if (hasParams) {
            reqParams.append("{");
            String mandatoryParameters =
                    element.getParameters().stream()
                            .filter(ApiParameter::isRequired)
                            .map(ApiParameter::getName)
                            .map(name -> "'" + name + "': " + name.toLowerCase(Locale.ROOT))
                            .collect(Collectors.joining(", "));
            reqParams.append(mandatoryParameters);
            reqParams.append("}");

            List<ApiParameter> optionalParameters =
                    element.getParameters().stream()
                            .filter(e -> !e.isRequired())
                            .collect(Collectors.toList());
            if (!optionalParameters.isEmpty()) {
                out.write("        params = ");
                out.write(reqParams.toString());
                out.write("\n");
                reqParams.replace(0, reqParams.length(), "params");

                for (ApiParameter parameter : optionalParameters) {
                    String name = parameter.getName();
                    String varName = name.toLowerCase(Locale.ROOT);
                    out.write("        if " + varName + " is not None:\n");
                    out.write("            params['" + name + "'] = " + varName + "\n");
                }
            }
        }

        if (type.equals(OTHER_ENDPOINT)) {
            out.write("        return (");
        } else {
            out.write("        return six.next(six.itervalues(");
        }
        out.write(
                "self.zap."
                        + method
                        + "(self.zap."
                        + baseUrl
                        + " + '"
                        + component
                        + "/"
                        + type
                        + "/"
                        + element.getName()
                        + "/'");

        // , {'url': url}))
        if (hasParams) {
            out.write(", ");
            out.write(reqParams.toString());
            out.write(")");
            if (!type.equals(OTHER_ENDPOINT)) {
                out.write("))");
            } else {
                out.write(")");
            }
        } else if (!type.equals(OTHER_ENDPOINT)) {
            out.write(")))");
        } else {
            out.write(")");
        }
    }

    @Override
    protected void generateAPIFiles(ApiImplementor imp) throws IOException {
        Path file = getDirectory().resolve(createFileName(imp.getPrefix()));
        System.out.println("Generating " + file.toAbsolutePath());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(HEADER);
            out.write("import six\n\n\n");
            out.write("class " + safeName(imp.getPrefix()) + "(object):\n\n");
            out.write("    def __init__(self, zap):\n");
            out.write("        self.zap = zap");

            for (ApiElement view : imp.getApiViews()) {
                this.generatePythonElement(view, imp.getPrefix(), VIEW_ENDPOINT, out);
            }
            for (ApiElement action : imp.getApiActions()) {
                this.generatePythonElement(action, imp.getPrefix(), ACTION_ENDPOINT, out);
            }
            for (ApiElement other : imp.getApiOthers()) {
                this.generatePythonElement(other, imp.getPrefix(), OTHER_ENDPOINT, out);
            }
            out.write("\n");
        }
    }

    private static String safeName(String name) {
        if (nameMap.containsKey(name)) {
            return nameMap.get(name);
        }
        return name;
    }

    private static String createFileName(String name) {
        return safeName(name) + ".py";
    }

    private static String createFunctionName(String name) {
        return removeAllFullStopCharacters(camelCaseToLcUnderscores(safeName(name)));
    }

    private static String removeAllFullStopCharacters(String string) {
        return string.replaceAll("\\.", "");
    }

    public static String camelCaseToLcUnderscores(String s) {
        // Ripped off / inspired by
        // http://stackoverflow.com/questions/2559759/how-do-i-convert-camelcase-into-human-readable-names-in-java
        return safeName(s)
                .replaceAll(
                        String.format(
                                "%s|%s|%s",
                                "(?<=[A-Z])(?=[A-Z][a-z])",
                                "(?<=[^A-Z])(?=[A-Z])",
                                "(?<=[A-Za-z])(?=[^A-Za-z])"),
                        "_")
                .toLowerCase();
    }

    public static void main(String[] args) throws Exception {
        // Command for generating a python version of the ZAP API

        if (!Files.exists(Paths.get(DEFAULT_OUTPUT_DIR))) {
            System.err.println(
                    "The directory does not exist: "
                            + Paths.get(DEFAULT_OUTPUT_DIR).toAbsolutePath());
            System.exit(1);
        }

        PythonAPIGenerator wapi = new PythonAPIGenerator();
        wapi.generateCoreAPIFiles();

        // System.out.println(camelCaseToLcUnderscores("TestCase"));

    }
}
