/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.ExpectedHighlightingData;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 2/9/2017.
 */
public class JsonSchemaSelfHighligthingTest extends JsonSchemaHeavyAbstractTest {
  public static final String BASE_PATH = "/tests/testData/jsonSchema/selfHighlighting";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testPatterns() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration pattern =
          new UserDefinedJsonSchemaConfiguration("pattern", moduleDir + "/patternSchema.json", false, Collections.emptyList());
        addSchema(pattern);
        myDoCompletion = false;
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/patternSchema.json");
      }

      @Override
      public void doCheck() {
        checkHighlighting(new ExpectedHighlightingData(new DocumentImpl("{\n" +
                                                                        "  \"properties\": {\n" +
                                                                        "    \"withPattern\": {\n" +
                                                                        "      \"pattern\": <warning descr=\"Unclosed character class near index 3\n" +
                                                                        "^[]$\n" +
                                                                        "   ^\">\"^[]$\"</warning>\n" +
                                                                        "    },\n" +
                                                                        "    \"everythingFine\": {\n" +
                                                                        "      \"pattern\": \"^[a]$\"\n" +
                                                                        "    }\n" +
                                                                        "  },\n" +
                                                                        "  \"patternProperties\": {\n" +
                                                                        "    <warning descr=\"Unclosed character class near index 8\n" +
                                                                        ".*p[0-9.*\n" +
                                                                        "        ^\">\"p[0-9\"</warning>: {},\n" +
                                                                        "    <warning descr=\"Unclosed character class near index 8\n" +
                                                                        ".*b[0-7.*\n" +
                                                                        "        ^\">\"b[0-7\"</warning>: {}\n" +
                                                                        "  }\n" +
                                                                        "}"), true, true, false, myFile));
      }
    });
  }
}
