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
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaAnnotator;
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
    doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}",
           "{ \"prop\": <warning descr=\"Is not multiple of 2\">3</warning>}");
  }

  public void testNumberMultipleCorrect() throws Exception {
    doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}", "{ \"prop\": 4}");
  }

  public void testNumberMinMax() throws Exception {
    doTest("{ \"properties\": { \"prop\": {\n" +
           "  \"type\": \"number\",\n" +
           "  \"minimum\": 0,\n" +
           "  \"maximum\": 100,\n" +
           "  \"exclusiveMaximum\": true\n" +
           "}}}", "{ \"prop\": 14}");
  }

  @SuppressWarnings("Duplicates")
  public void testEnum() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"enum\": [1,2,3,\"18\"]}}}";
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3, \\\"18\\\"]\">18</warning>}");
    doTest(schema, "{\"prop\": 2}");
    doTest(schema, "{\"prop\": \"18\"}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3, \\\"18\\\"]\">\"2\"</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testSimpleString() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"minLength\": 2, \"maxLength\": 3}}}";
    doTest(schema, "{\"prop\": <warning descr=\"String is shorter than 2\">\"s\"</warning>}");
    doTest(schema, "{\"prop\": \"sh\"}");
    doTest(schema, "{\"prop\": \"sho\"}");
    doTest(schema, "{\"prop\": <warning descr=\"String is longer than 3\">\"shor\"</warning>}");
  }

  public void testArray() throws Exception {
    final String schema = schema("{\n" +
                                 "  \"type\": \"array\",\n" +
                                 "  \"items\": {\n" +
                                 "    \"type\": \"number\", \"minimum\": 18" +
                                 "  }\n" +
                                 "}");
    doTest(schema, "{\"prop\": [101, 102]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Less than a minimum 18\">16</warning>]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Type is not allowed\">\"test\"</warning>]}");
  }

  public void testTopLevelArray() throws Exception {
    final String schema = "{\n" +
                                 "  \"type\": \"array\",\n" +
                                 "  \"items\": {\n" +
                                 "    \"type\": \"number\", \"minimum\": 18" +
                                 "  }\n" +
                                 "}";
    doTest(schema, "[101, 102]");
  }

  public void testTopLevelObjectArray() throws Exception {
    final String schema = "{\n" +
                                 "  \"type\": \"array\",\n" +
                                 "  \"items\": {\n" +
                                 "    \"type\": \"object\", \"properties\": {\"a\": {\"type\": \"number\"}}" +
                                 "  }\n" +
                                 "}";
    doTest(schema, "[{\"a\": <warning descr=\"Type is not allowed\">true</warning>}]");
    doTest(schema, "[{\"a\": 18}]");
  }

  public void testArrayTuples1() throws Exception {
    final String schema = schema("{\n" +
                                 "  \"type\": \"array\",\n" +
                                 "  \"items\": [{\n" +
                                 "    \"type\": \"number\", \"minimum\": 18" +
                                 "  }, {\"type\" : \"string\"}]\n" +
                                 "}");
    doTest(schema, "{\"prop\": [101, <warning descr=\"Type is not allowed\">102</warning>]}");
    doTest(schema, "{\"prop\": [101, \"102\"]}");
    doTest(schema, "{\"prop\": [101, \"102\", \"additional\"]}");

    final String schema2 = schema("{\n" +
                                  "  \"type\": \"array\",\n" +
                                  "  \"items\": [{\n" +
                                  "    \"type\": \"number\", \"minimum\": 18" +
                                  "  }, {\"type\" : \"string\"}],\n" +
                                  "\"additionalItems\": false}");
    doTest(schema2, "{\"prop\": [101, \"102\", <warning descr=\"Additional items are not allowed\">\"additional\"</warning>]}");
  }

  public void testArrayLength() throws Exception {
    final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    doTest(schema, "{\"prop\": <warning descr=\"Array is shorter than 2\">[]</warning>}");
    doTest(schema, "{\"prop\": [1,2]}");
    doTest(schema, "{\"prop\": <warning descr=\"Array is longer than 3\">[1,2,3,4]</warning>}");
  }

  public void testArrayUnique() throws Exception {
    final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    doTest(schema, "{\"prop\": [1,2]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Item is not unique\">1</warning>,2, \"test\", <warning descr=\"Item is not unique\">1</warning>]}");
  }

  public void testMetadataIsOk() throws Exception {
    final String schema = "{\n" +
                          "  \"title\" : \"Match anything\",\n" +
                          "  \"description\" : \"This is a schema that matches anything.\",\n" +
                          "  \"default\" : \"Default value\"\n" +
                          "}";
    doTest(schema, "{\"anything\": 1}");
  }

  public void testRequiredField() throws Exception {
    final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    doTest(schema, "{\"a\": 11}");
    doTest(schema, "{\"a\": 1, \"b\": true}");
    doTest(schema, "<warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>");
  }

  public void testInnerRequired() throws Exception {
    final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    doTest(schema, "{\"prop\": {\"a\": 11}}");
    doTest(schema, "{\"prop\": {\"a\": 1, \"b\": true}}");
    doTest(schema, "{\"prop\": <warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>}");
  }

  public void testUseDefinition() throws Exception {
    final String schema = "{\"definitions\": {\"address\": {\"type\": \"object\", \"properties\": {\"street\": {\"type\": \"string\"}," +
                          "\"house\": {\"type\": \"integer\"}}}}," +
                          "\"type\": \"object\", \"properties\": {" +
                          "\"home\": {\"$ref\": \"#/definitions/address\"}, " +
                          "\"office\": {\"$ref\": \"#/definitions/address\"}" +
                          "}}";
    doTest(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": 11}}");
    doTest(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": <warning descr=\"Type is not allowed\">\"unknown\"</warning>}," +
                   "\"office\": {\"street\": <warning descr=\"Type is not allowed\">5</warning>}}");
  }

  public void testAdditionalPropertiesAllowed() throws Exception {
    final String schema = schema("{}");
    doTest(schema, "{\"prop\": {}, \"someStuff\": 20}");
  }

  public void testAdditionalPropertiesDisabled() throws Exception {
    final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    doTest(schema, "{\"prop\": {}, <warning descr=\"Property 'someStuff' is not allowed\">\"someStuff\": 20</warning>}");
  }

  public void testAdditionalPropertiesSchema() throws Exception {
    final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                          "\"additionalProperties\": {\"type\": \"string\"}}";
    doTest(schema, "{\"a\" : 18, \"b\": \"wall\", \"c\": <warning descr=\"Type is not allowed\">11</warning>}");
  }

  public void testMinMaxProperties() throws Exception {
    final String schema = "{\"type\": \"object\", \"minProperties\": 1, \"maxProperties\": 2}";
    doTest(schema, "<warning descr=\"Number of properties is less than 1\">{}</warning>");
    doTest(schema, "{\"a\": 1}");
    doTest(schema, "<warning descr=\"Number of properties is greater than 2\">{\"a\": 1, \"b\": 22, \"c\": 33}</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"abc\"}");
    doTest(schema, "{\"prop\": true}");
    doTest(schema, "{\"prop\": <warning descr=\"Type is not allowed\">11</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOfForTwoMatches() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"b\"}");
    doTest(schema, "{\"prop\": \"c\"}");
    doTest(schema, "{\"prop\": <warning descr=\"Validates to more than one variant\">\"a\"</warning>}");
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
    doTest(schema, "{\"prop\": \"off\"}");
    doTest(schema, "{\"prop\": 12}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [\\\"off\\\", \\\"warn\\\", \\\"error\\\"]\">\"wrong\"</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testAnyOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"b\"}");
    doTest(schema, "{\"prop\": \"c\"}");
    doTest(schema, "{\"prop\": \"a\"}");
  }

  @SuppressWarnings("Duplicates")
  public void testAllOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"integer\", \"multipleOf\": 2}");
    subSchemas.add("{\"enum\": [1,2,3]}");
    final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": <warning descr=\"Is not multiple of 2\">1</warning>}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3]\">4</warning>}");
    doTest(schema, "{\"prop\": 2}");
  }

  public void testObjectInArray() throws Exception {
    final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                 "\"properties\": {" +
                                 "\"innerType\":{}, \"innerValue\":{}" +
                                 "}, \"additionalProperties\": false" +
                                 "}}");
    doTest(schema, "{\"prop\": [{\"innerType\":{}, <warning descr=\"Property 'alien' is not allowed\">\"alien\":{}</warning>}]}");
  }

  public void testObjectDeeperInArray() throws Exception {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                 "\"properties\": {" +
                                 "\"innerType\":" + innerTypeSchema +
                                 "}, \"additionalProperties\": false" +
                                 "}}");
    doTest(schema,
           "{\"prop\": [{\"innerType\":{\"only\": true, <warning descr=\"Property 'hidden' is not allowed\">\"hidden\": false</warning>}}]}");
  }

  public void testInnerObjectPropValueInArray() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    doTest(schema, "{\"prop\": [1,3]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Value should be one of: [1, 2, 3]\">\"out\"</warning>]}");
  }

  public void testAllOfProperties() throws Exception {
    final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                          " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
    doTest(schema, "{\"first\": {}, \"second\": null}");
    doTest(schema, "{\"first\": {}, \"second\": 44, <warning descr=\"Property 'other' is not allowed\">\"other\": 15</warning>}");
    doTest(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: [33, 44]\">12</warning>}");
  }

  public void testWithWaySelection() throws Exception {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    //doTest(schema, "{\"prop\": [{\"kilo\": 20}]}");
    //doTest(schema, "{\"prop\": 5}");
    doTest(schema, "{\"prop\": [{<warning descr=\"Property 'foxtrot' is not allowed\">\"foxtrot\": 15</warning>, \"kilo\": 20}]}");
  }

  public void testIntegerTypeWithMinMax() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax_schema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax.json"));
    doTest(schemaText, inputText);
  }

  public void testOneOf1() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOf1.json"));
    doTest(schemaText, inputText);
  }

  public void testOneOf2() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOf2.json"));
    doTest(schemaText, inputText);
  }

  public void testAnyOnePropertySelection() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOnePropertySelectionSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOnePropertySelection.json"));
    doTest(schemaText, inputText);
  }

  public void testAnyOneTypeSelection() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOneTypeSelectionSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOneTypeSelection.json"));
    doTest(schemaText, inputText);
  }

  public void testOneOfWithEmptyPropertyValue() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfWithEmptyPropertyValue.json"));
    doTest(schemaText, inputText);
  }

  public void testCycledSchema() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledSchema.json"));
    doTest(schemaText, inputText);
  }

  public void testWithRootRefCycledSchema() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledWithRootRefSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledWithRootRefSchema.json"));
    doTest(schemaText, inputText);
  }

  public void testCycledWithRootRefInNotSchema() throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledWithRootRefInNotSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledWithRootRefInNotSchema.json"));
    doTest(schemaText, inputText);
  }

  public void testPatternPropertiesHighlighting() throws Exception {
    final String schema = "{\n" +
                          "  \"patternProperties\": {\n" +
                          "    \"^A\" : {\n" +
                          "      \"type\": \"number\"\n" +
                          "    },\n" +
                          "    \"B\": {\n" +
                          "      \"type\": \"boolean\"\n" +
                          "    },\n" +
                          "    \"C\": {\n" +
                          "      \"enum\": [\"test\", \"em\"]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    doTest(schema, "{\n" +
                   "  \"Abezjana\": 2,\n" +
                   "  \"Auto\": <warning descr=\"Type is not allowed\">\"no\"</warning>,\n" +
                   "  \"ABe\": <warning descr=\"Type is not allowed\">22</warning>,\n" +
                   "  \"Boloto\": <warning descr=\"Type is not allowed\">2</warning>,\n" +
                   "  \"Cyan\": <warning descr=\"Value should be one of: [\\\"test\\\", \\\"em\\\"]\">\"me\"</warning>\n" +
                   "}");
  }

  public void testPatternPropertiesFromIssue() throws Exception {
    final String schema = "{\n" +
                          "  \"type\": \"object\",\n" +
                          "  \"additionalProperties\": false,\n" +
                          "  \"patternProperties\": {\n" +
                          "    \"p[0-9]\": {\n" +
                          "      \"type\": \"string\"\n" +
                          "    },\n" +
                          "    \"a[0-9]\": {\n" +
                          "      \"enum\": [\"auto!\"]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    doTest(schema, "{\n" +
                   "  \"p1\": <warning descr=\"Type is not allowed\">1</warning>,\n" +
                   "  \"p2\": \"3\",\n" +
                   "  \"a2\": \"auto!\",\n" +
                   "  \"a1\": <warning descr=\"Value should be one of: [\\\"auto!\\\"]\">\"moto!\"</warning>\n" +
                   "}");
  }

  public void testPatternForPropertyValue() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"withPattern\": {\n" +
                          "      \"pattern\": \"p[0-9]\"\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    final String correctText = "{\n" +
                               "  \"withPattern\": \"p1\"\n" +
                               "}";
    final String wrongText = "{\n" +
                             "  \"withPattern\": <warning descr=\"String is violating the pattern: 'p[0-9]'\">\"wrong\"</warning>\n" +
                             "}";
    doTest(schema, correctText);
    doTest(schema, wrongText);
  }

  public void testPatternWithSpecialEscapedSymbols() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"withPattern\": {\n" +
                          "      \"pattern\": \"^\\\\d{4}\\\\-(0?[1-9]|1[012])\\\\-(0?[1-9]|[12][0-9]|3[01])$\"\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    final String correctText = "{\n" +
                               "  \"withPattern\": \"1234-11-11\"\n" +
                               "}";
    final String wrongText = "{\n" +
                             "  \"withPattern\": <warning descr=\"String is violating the pattern: '^\\d{4}\\-(0?[1-9]|1[012])\\-(0?[1-9]|[12][0-9]|3[01])$'\">\"wrong\"</warning>\n" +
                             "}";
    doTest(schema, correctText);
    doTest(schema, wrongText);
  }

  public void testRootObjectRedefinedAdditionalPropertiesForbidden() throws Exception {
    doTest(rootObjectRedefinedSchema(), "{<warning descr=\"Property 'a' is not allowed\">\"a\": true</warning>," +
                                        "\"r1\": \"allowed!\"}");
  }

  public void testNumberOfSameNamedPropertiesCorrectlyChecked() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"size\": {\n" +
                          "      \"type\": \"object\",\n" +
                          "      \"minProperties\": 2,\n" +
                          "      \"maxProperties\": 3,\n" +
                          "      \"properties\": {\n" +
                          "        \"a\": {\n" +
                          "          \"type\": \"boolean\"\n" +
                          "        }\n" +
                          "      }\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    doTest(schema, "{\n" +
                   "  \"size\": <warning descr=\"Number of properties is greater than 3\">{\n" +
                   "    \"a\": <warning descr=\"Type is not allowed\">1</warning>," +
                   " \"b\":3, \"c\": 4, " +
                   "\"a\": <warning descr=\"Type is not allowed\">5</warning>\n" +
                   "  }</warning>\n" +
                   "}");
  }

  public void testManyDuplicatesInArray() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"array\":{\n" +
                          "      \"type\": \"array\",\n" +
                          "      \"uniqueItems\": true\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    doTest(schema, "{\"array\": [<warning descr=\"Item is not unique\">1</warning>," +
                   "<warning descr=\"Item is not unique\">1</warning>," +
                   "<warning descr=\"Item is not unique\">1</warning>," +
                   "<warning descr=\"Item is not unique\">2</warning>," +
                   "<warning descr=\"Item is not unique\">2</warning>," +
                   "5," +
                   "<warning descr=\"Item is not unique\">3</warning>," +
                   "<warning descr=\"Item is not unique\">3</warning>]}");
  }

  public void testPropertyValueAlsoHighlightedIfPatternIsInvalid() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"withPattern\": {\n" +
                          "      \"pattern\": \"^[]$\"\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    final String text = "{\"withPattern\":" +
                        " <warning descr=\"Can not check string by pattern because of error: Unclosed character class near index 3\n^[]$\n   ^\">\"(124)555-4216\"</warning>}";
    doTest(schema, text);
  }

  public void testNotSchema() throws Exception {
    final String schema = "{\"properties\": {\n" +
                          "    \"not_type\": { \"not\": { \"type\": \"string\" } }\n" +
                          "  }}";
    doTest(schema, "{\"not_type\": <warning descr=\"Validates against 'not' schema\">\"wrong\"</warning>}");
  }

  public void testNotSchemaCombinedWithNormal() throws Exception {
    final String schema = "{\"properties\": {\n" +
                          "    \"not_type\": {\n" +
                          "      \"pattern\": \"^[a-z]*[0-5]*$\",\n" +
                          "      \"not\": { \"pattern\": \"^[a-z]{1}[0-5]$\" }\n" +
                          "    }\n" +
                          "  }}";
    doTest(schema, "{\"not_type\": \"va4\"}");
    doTest(schema, "{\"not_type\": <warning descr=\"Validates against 'not' schema\">\"a4\"</warning>}");
    doTest(schema, "{\"not_type\": <warning descr=\"String is violating the pattern: '^[a-z]*[0-5]*$'\">\"4a4\"</warning>}");
  }

  public void testDoNotMarkOneOfThatDiffersWithFormat() throws Exception {
    final String schema = "{\n" +
                          "\n" +
                          "  \"properties\": {\n" +
                          "    \"withFormat\": {\n" +
                          "      \"type\": \"string\"," +
                          "      \"oneOf\": [\n" +
                          "        {\n" +
                          "          \"format\":\"hostname\"\n" +
                          "        },\n" +
                          "        {\n" +
                          "          \"format\": \"ip4\"\n" +
                          "        }\n" +
                          "      ]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    doTest(schema, "{\"withFormat\": \"localhost\"}");
  }

  public void testAcceptSchemaWithoutType() throws Exception {
    final String schema = "{\n" +
                          "\n" +
                          "  \"properties\": {\n" +
                          "    \"withFormat\": {\n" +
                          "      \"oneOf\": [\n" +
                          "        {\n" +
                          "          \"format\":\"hostname\"\n" +
                          "        },\n" +
                          "        {\n" +
                          "          \"format\": \"ip4\"\n" +
                          "        }\n" +
                          "      ]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    doTest(schema, "{\"withFormat\": \"localhost\"}");
  }

  public static String rootObjectRedefinedSchema() {
    return "{\n" +
           "  \"$schema\": \"http://json-schema.org/draft-04/schema#\",\n" +
           "  \"type\": \"object\",\n" +
           "  \"$ref\" : \"#/definitions/root\",\n" +
           "  \"definitions\": {\n" +
           "    \"root\" : {\n" +
           "      \"type\": \"object\",\n" +
           "      \"additionalProperties\": false,\n" +
           "      \"properties\": {\n" +
           "        \"r1\": {\n" +
           "          \"type\": \"string\"\n" +
           "        },\n" +
           "        \"r2\": {\n" +
           "          \"type\": \"string\"\n" +
           "        }\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}\n";
  }

  static String schema(final String s) {
    return "{\"type\": \"object\", \"properties\": {\"prop\": " + s + "}}";
  }

  private void doTest(@NotNull final String schema, @NotNull final String text) throws Exception {
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
    //noinspection ResultOfMethodCallIgnored
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
