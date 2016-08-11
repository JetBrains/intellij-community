/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaAnnotator;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Irina.Chernushina on 9/21/2015.
 */
public class JsonSchemaHighlightingTest extends DaemonAnalyzerTestCase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/jsonSchema/highlighting";
  }

  public void testNumberMultipleWrong() throws Exception {
    testImpl("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}",
             "{ \"prop\": <warning descr=\"Is not multiple of 3\">3</warning>}");
  }

  public void testNumberMultipleCorrect() throws Exception {
    testImpl("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}", "{ \"prop\": 4}");
  }

  public void testNumberMinMax() throws Exception {
    testImpl("{ \"properties\": { \"prop\": {\n" +
             "  \"type\": \"number\",\n" +
             "  \"minimum\": 0,\n" +
             "  \"maximum\": 100,\n" +
             "  \"exclusiveMaximum\": true\n" +
             "}}}", "{ \"prop\": 14}");
  }

  @SuppressWarnings("Duplicates")
  public void testEnum() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"enum\": [1,2,3,\"18\"]}}}";
    testImpl(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3, \\\"18\\\"]\">18</warning>}");
    testImpl(schema, "{\"prop\": 2}");
    testImpl(schema, "{\"prop\": \"18\"}");
    testImpl(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3, \\\"18\\\"]\">\"2\"</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testSimpleString() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"minLength\": 2, \"maxLength\": 3}}}";
    testImpl(schema, "{\"prop\": <warning descr=\"String is shorter than 2\">\"s\"</warning>}");
    testImpl(schema, "{\"prop\": \"sh\"}");
    testImpl(schema, "{\"prop\": \"sho\"}");
    testImpl(schema, "{\"prop\": <warning descr=\"String is longer than 3\">\"shor\"</warning>}");
  }

  public void testArray() throws Exception {
    final String schema = schema("{\n" +
                                 "  \"type\": \"array\",\n" +
                                 "  \"items\": {\n" +
                                 "    \"type\": \"number\", \"minimum\": 18" +
                                 "  }\n" +
                                 "}");
    testImpl(schema, "{\"prop\": [101, 102]}");
    testImpl(schema, "{\"prop\": [<warning descr=\"Less than a minimum 18.0\">16</warning>]}");
    testImpl(schema, "{\"prop\": [<warning descr=\"Type is not allowed\">\"test\"</warning>]}");
  }

  public void testArrayTuples1() throws Exception {
    final String schema = schema("{\n" +
                                 "  \"type\": \"array\",\n" +
                                 "  \"items\": [{\n" +
                                 "    \"type\": \"number\", \"minimum\": 18" +
                                 "  }, {\"type\" : \"string\"}]\n" +
                                 "}");
    testImpl(schema, "{\"prop\": [101, <warning descr=\"Type is not allowed\">102</warning>]}");
    testImpl(schema, "{\"prop\": [101, \"102\"]}");
    testImpl(schema, "{\"prop\": [101, \"102\", \"additional\"]}");

    final String schema2 = schema("{\n" +
                                  "  \"type\": \"array\",\n" +
                                  "  \"items\": [{\n" +
                                  "    \"type\": \"number\", \"minimum\": 18" +
                                  "  }, {\"type\" : \"string\"}],\n" +
                                  "\"additionalItems\": false}");
    testImpl(schema2, "{\"prop\": [101, \"102\", <warning descr=\"Additional items are not allowed\">\"additional\"</warning>]}");
  }

  public void testArrayLength() throws Exception {
    final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    testImpl(schema, "{\"prop\": <warning descr=\"Array is shorter than 2\">[]</warning>}");
    testImpl(schema, "{\"prop\": [1,2]}");
    testImpl(schema, "{\"prop\": <warning descr=\"Array is longer than 3\">[1,2,3,4]</warning>}");
  }

  public void testArrayUnique() throws Exception {
    final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    testImpl(schema, "{\"prop\": [1,2]}");
    testImpl(schema, "{\"prop\": [1,2, \"test\", <warning descr=\"Item is not unique\">1</warning>]}");
  }

  public void testMetadataIsOk() throws Exception {
    final String schema = "{\n" +
                          "  \"title\" : \"Match anything\",\n" +
                          "  \"description\" : \"This is a schema that matches anything.\",\n" +
                          "  \"default\" : \"Default value\"\n" +
                          "}";
    testImpl(schema, "{\"anything\": 1}");
  }

  public void testRequiredField() throws Exception {
    final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    testImpl(schema, "{\"a\": 11}");
    testImpl(schema, "{\"a\": 1, \"b\": true}");
    testImpl(schema, "<warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>");
  }

  public void testInnerRequired() throws Exception {
    final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    testImpl(schema, "{\"prop\": {\"a\": 11}}");
    testImpl(schema, "{\"prop\": {\"a\": 1, \"b\": true}}");
    testImpl(schema, "{\"prop\": <warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>}");
  }

  public void testUseDefinition() throws Exception {
    final String schema = "{\"definitions\": {\"address\": {\"type\": \"object\", \"properties\": {\"street\": {\"type\": \"string\"}," +
                          "\"house\": {\"type\": \"integer\"}}}}," +
                          "\"type\": \"object\", \"properties\": {" +
                          "\"home\": {\"$ref\": \"#/definitions/address\"}, " +
                          "\"office\": {\"$ref\": \"#/definitions/address\"}" +
                          "}}";
    testImpl(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": 11}}");
    testImpl(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": <warning descr=\"Type is not allowed\">\"unknown\"</warning>}," +
                     "\"office\": {\"street\": <warning descr=\"Type is not allowed\">5</warning>}}");
  }

  public void testAdditionalPropertiesAllowed() throws Exception {
    final String schema = schema("{}");
    testImpl(schema, "{\"prop\": {}, \"someStuff\": 20}");
  }

  public void testAdditionalPropertiesDisabled() throws Exception {
    final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    testImpl(schema, "{\"prop\": {}, <warning descr=\"Property 'someStuff' is not allowed\">\"someStuff\": 20</warning>}");
  }

  public void testAdditionalPropertiesSchema() throws Exception {
    final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                          "\"additionalProperties\": {\"type\": \"string\"}}";
    testImpl(schema, "{\"a\" : 18, \"b\": \"wall\", \"c\": <warning descr=\"Type is not allowed\">11</warning>}");
  }

  public void testMinMaxProperties() throws Exception {
    final String schema = "{\"type\": \"object\", \"minProperties\": 1, \"maxProperties\": 2}";
    testImpl(schema, "<warning descr=\"Number of properties is less than 1\">{}</warning>");
    testImpl(schema, "{\"a\": 1}");
    testImpl(schema, "<warning descr=\"Number of properties is greater than 2\">{\"a\": 1, \"b\": 22, \"c\": 33}</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    testImpl(schema, "{\"prop\": \"abc\"}");
    testImpl(schema, "{\"prop\": true}");
    testImpl(schema, "{\"prop\": <warning descr=\"Type is not allowed\">11</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOfForTwoMatches() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    testImpl(schema, "{\"prop\": \"b\"}");
    testImpl(schema, "{\"prop\": \"c\"}");
    testImpl(schema, "{\"prop\": <warning descr=\"Validates to more than one variant\">\"a\"</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOfSelectError() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\",\n" +
                   "          \"enum\": [\n" +
                   "            \"off\", \"warn\", \"error\"\n" +
                   "          ]}");
    subSchemas.add("{\"type\": \"integer\"}");
    final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    testImpl(schema, "{\"prop\": \"off\"}");
    testImpl(schema, "{\"prop\": 12}");
    testImpl(schema, "{\"prop\": <warning descr=\"Value should be one of: [\\\"off\\\", \\\"warn\\\", \\\"error\\\"]\">\"wrong\"</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testAnyOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    testImpl(schema, "{\"prop\": \"b\"}");
    testImpl(schema, "{\"prop\": \"c\"}");
    testImpl(schema, "{\"prop\": \"a\"}");
  }

  @SuppressWarnings("Duplicates")
  public void testAllOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    testImpl(schema, "{\"prop\": <warning descr=\"Value should be one of: [\\\"a\\\", \\\"c\\\"]\">\"b\"</warning>}");
    testImpl(schema, "{\"prop\": <warning descr=\"Value should be one of: [\\\"a\\\", \\\"b\\\"]\">\"c\"</warning>}");
    testImpl(schema, "{\"prop\": \"a\"}");
  }

  public void testObjectInArray() throws Exception {
    final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                 "\"properties\": {" +
                                 "\"innerType\":{}, \"innerValue\":{}" +
                                 "}, \"additionalProperties\": false" +
                                 "}}");
    testImpl(schema, "{\"prop\": [{\"innerType\":{}, <warning descr=\"Property 'alien' is not allowed\">\"alien\":{}</warning>}]}");
  }

  public void testObjectDeeperInArray() throws Exception {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                 "\"properties\": {" +
                                 "\"innerType\":" + innerTypeSchema +
                                 "}, \"additionalProperties\": false" +
                                 "}}");
    testImpl(schema,
             "{\"prop\": [{\"innerType\":{\"only\": true, <warning descr=\"Property 'hidden' is not allowed\">\"hidden\": false</warning>}}]}");
  }

  public void testInnerObjectPropValueInArray() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    testImpl(schema, "{\"prop\": [1,3]}");
    testImpl(schema, "{\"prop\": [<warning descr=\"Value should be one of: [1, 2, 3]\">\"out\"</warning>]}");
  }

  public void testAllOfProperties() throws Exception {
    final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                          " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
    testImpl(schema, "{\"first\": {}, \"second\": null}");
    testImpl(schema, "{\"first\": {}, \"second\": 44, \"other\": 15}");
    testImpl(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: [33, 44]\">12</warning>}");
  }

  public void testWithWaySelection() throws Exception {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    testImpl(schema, "{\"prop\": [{\"kilo\": 20}]}");
    testImpl(schema, "{\"prop\": 5}");
    testImpl(schema, "{\"prop\": [{<warning descr=\"Property 'foxtrot' is not allowed\">\"foxtrot\": 15</warning>, \"kilo\": 20}]}");
  }

  public void testIntegerTypeWithMinMax() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax_schema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax.json"));
    testImpl(schemaText, inputText);
  }

  static String schema(final String s) {
    return "{\"type\": \"object\", \"properties\": {\"prop\": " + s + "}}";
  }

  private void testImpl(@NotNull final String schema, @NotNull final String text) throws Exception {
    final PsiFile file = createFile(myModule, "config.json", text);

    final Annotator annotator = new JsonSchemaAnnotator();

    registerProvider(getProject(), schema);
    LanguageAnnotators.INSTANCE.addExplicitExtension(JsonLanguage.INSTANCE, annotator);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        LanguageAnnotators.INSTANCE.removeExplicitExtension(JsonLanguage.INSTANCE, annotator);
        JsonSchemaTestServiceImpl.setProvider(null);
      }
    });
    configureByFile(file.getVirtualFile());
    doTest(file.getVirtualFile(), true, false);
  }

  public static void registerProvider(Project project, @NotNull String schema) throws IOException {
    File dir = PlatformTestCase.createTempDir("json_schema_test", true);
    File child = new File(dir, "schema.json");
    child.createNewFile();
    FileUtil.writeToFile(child, schema);
    VirtualFile schemaFile = getVirtualFile(child);
    JsonSchemaTestServiceImpl.setProvider(new JsonSchemaTestProvider(schemaFile));
    AreaPicoContainer container = Extensions.getArea(project).getPicoContainer();
    String key = JsonSchemaService.class.getName();
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, JsonSchemaTestServiceImpl.class);
  }
}
