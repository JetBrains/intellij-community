// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Irina.Chernushina on 9/21/2015.
 */
public class JsonSchemaHighlightingTest extends JsonSchemaHighlightingTestBase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/jsonSchema/highlighting";
  }

  @Override
  protected String getTestFileName() {
    return "config.json";
  }

  @Override
  protected InspectionProfileEntry getInspectionProfile() {
    return new JsonSchemaComplianceInspection();
  }

  @Override
  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      JsonLanguage.INSTANCE);
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
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"enum\": [1,2,3,\"18\"]}}}";
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3, \\\"18\\\"]\">18</warning>}");
    doTest(schema, "{\"prop\": 2}");
    doTest(schema, "{\"prop\": \"18\"}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3, \\\"18\\\"]\">\"2\"</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testSimpleString() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"minLength\": 2, \"maxLength\": 3}}}";
    doTest(schema, "{\"prop\": <warning descr=\"String is shorter than 2\">\"s\"</warning>}");
    doTest(schema, "{\"prop\": \"sh\"}");
    doTest(schema, "{\"prop\": \"sho\"}");
    doTest(schema, "{\"prop\": <warning descr=\"String is longer than 3\">\"shor\"</warning>}");
  }

  public void testArray() throws Exception {
    @Language("JSON") final String schema = schema("{\n" +
                                                   "  \"type\": \"array\",\n" +
                                                   "  \"items\": {\n" +
                                                   "    \"type\": \"number\", \"minimum\": 18" +
                                                   "  }\n" +
                                                   "}");
    doTest(schema, "{\"prop\": [101, 102]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Less than a minimum 18\">16</warning>]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Type is not allowed. Expected: number.\">\"test\"</warning>]}");
  }

  public void testTopLevelArray() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"type\": \"array\",\n" +
                                            "  \"items\": {\n" +
                                            "    \"type\": \"number\", \"minimum\": 18" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "[101, 102]");
  }

  public void testTopLevelObjectArray() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"type\": \"array\",\n" +
                                            "  \"items\": {\n" +
                                            "    \"type\": \"object\", \"properties\": {\"a\": {\"type\": \"number\"}}" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "[{\"a\": <warning descr=\"Type is not allowed. Expected: number.\">true</warning>}]");
    doTest(schema, "[{\"a\": 18}]");
  }

  public void testArrayTuples1() throws Exception {
    @Language("JSON") final String schema = schema("{\n" +
                                                   "  \"type\": \"array\",\n" +
                                                   "  \"items\": [{\n" +
                                                   "    \"type\": \"number\", \"minimum\": 18" +
                                                   "  }, {\"type\" : \"string\"}]\n" +
                                                   "}");
    doTest(schema, "{\"prop\": [101, <warning descr=\"Type is not allowed. Expected: string.\">102</warning>]}");
    doTest(schema, "{\"prop\": [101, \"102\"]}");
    doTest(schema, "{\"prop\": [101, \"102\", \"additional\"]}");

    @Language("JSON") final String schema2 = schema("{\n" +
                                                    "  \"type\": \"array\",\n" +
                                                    "  \"items\": [{\n" +
                                                    "    \"type\": \"number\", \"minimum\": 18" +
                                                    "  }, {\"type\" : \"string\"}],\n" +
                                                    "\"additionalItems\": false}");
    doTest(schema2, "{\"prop\": [101, \"102\", <warning descr=\"Additional items are not allowed\">\"additional\"</warning>]}");
  }

  public void testArrayLength() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    doTest(schema, "{\"prop\": <warning descr=\"Array is shorter than 2\">[]</warning>}");
    doTest(schema, "{\"prop\": [1,2]}");
    doTest(schema, "{\"prop\": <warning descr=\"Array is longer than 3\">[1,2,3,4]</warning>}");
  }

  public void testArrayUnique() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    doTest(schema, "{\"prop\": [1,2]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Item is not unique\">1</warning>,2, \"test\", <warning descr=\"Item is not unique\">1</warning>]}");
  }

  public void testMetadataIsOk() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"title\" : \"Match anything\",\n" +
                                            "  \"description\" : \"This is a schema that matches anything.\",\n" +
                                            "  \"default\" : \"Default value\"\n" +
                                            "}";
    doTest(schema, "{\"anything\": 1}");
  }

  public void testRequiredField() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    doTest(schema, "{\"a\": 11}");
    doTest(schema, "{\"a\": 1, \"b\": true}");
    doTest(schema, "<warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>");
  }

  public void testInnerRequired() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    doTest(schema, "{\"prop\": {\"a\": 11}}");
    doTest(schema, "{\"prop\": {\"a\": 1, \"b\": true}}");
    doTest(schema, "{\"prop\": <warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>}");
  }

  public void testUseDefinition() throws Exception {
    @Language("JSON") final String schema = "{\"definitions\": {\"address\": {\"type\": \"object\", \"properties\": {\"street\": {\"type\": \"string\"}," +
                                            "\"house\": {\"type\": \"integer\"}}}}," +
                                            "\"type\": \"object\", \"properties\": {" +
                                            "\"home\": {\"$ref\": \"#/definitions/address\"}, " +
                                            "\"office\": {\"$ref\": \"#/definitions/address\"}" +
                                            "}}";
    doTest(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": 11}}");
    doTest(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": <warning descr=\"Type is not allowed. Expected: integer.\">\"unknown\"</warning>}," +
                   "\"office\": {\"street\": <warning descr=\"Type is not allowed. Expected: string.\">5</warning>}}");
  }

  public void testAdditionalPropertiesAllowed() throws Exception {
    @Language("JSON") final String schema = schema("{}");
    doTest(schema, "{\"prop\": {}, \"someStuff\": 20}");
  }

  public void testAdditionalPropertiesDisabled() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    doTest(schema, "{\"prop\": {}, <warning descr=\"Property 'someStuff' is not allowed\">\"someStuff\": 20</warning>}");
  }

  public void testAdditionalPropertiesSchema() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                                            "\"additionalProperties\": {\"type\": \"string\"}}";
    doTest(schema, "{\"a\" : 18, \"b\": \"wall\", \"c\": <warning descr=\"Type is not allowed. Expected: string.\">11</warning>}");
  }

  public void testMinMaxProperties() throws Exception {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"minProperties\": 1, \"maxProperties\": 2}";
    doTest(schema, "<warning descr=\"Number of properties is less than 1\">{}</warning>");
    doTest(schema, "{\"a\": 1}");
    doTest(schema, "<warning descr=\"Number of properties is greater than 2\">{\"a\": 1, \"b\": 22, \"c\": 33}</warning>");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"abc\"}");
    doTest(schema, "{\"prop\": true}");
    doTest(schema, "{\"prop\": <warning descr=\"Type is not allowed. Expected one of: boolean, string.\">11</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testOneOfForTwoMatches() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
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
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"off\"}");
    doTest(schema, "{\"prop\": 12}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [\\\"off\\\", \\\"warn\\\", \\\"error\\\"]\">\"wrong\"</warning>}");
  }

  @SuppressWarnings("Duplicates")
  public void testAnyOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"b\"}");
    doTest(schema, "{\"prop\": \"c\"}");
    doTest(schema, "{\"prop\": \"a\"}");
  }

  @SuppressWarnings("Duplicates")
  public void testAllOf() throws Exception {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"integer\", \"multipleOf\": 2}");
    subSchemas.add("{\"enum\": [1,2,3]}");
    @Language("JSON") final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": <warning descr=\"Is not multiple of 2\">1</warning>}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: [1, 2, 3]\">4</warning>}");
    doTest(schema, "{\"prop\": 2}");
  }

  public void testObjectInArray() throws Exception {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":{}, \"innerValue\":{}" +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTest(schema, "{\"prop\": [{\"innerType\":{}, <warning descr=\"Property 'alien' is not allowed\">\"alien\":{}</warning>}]}");
  }

  public void testObjectDeeperInArray() throws Exception {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":" + innerTypeSchema +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTest(schema,
           "{\"prop\": [{\"innerType\":{\"only\": true, <warning descr=\"Property 'hidden' is not allowed\">\"hidden\": false</warning>}}]}");
  }

  public void testInnerObjectPropValueInArray() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    doTest(schema, "{\"prop\": [1,3]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Value should be one of: [1, 2, 3]\">\"out\"</warning>]}");
  }

  public void testAllOfProperties() throws Exception {
    @Language("JSON") final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                                                                                " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
    doTest(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: [33, 44]\">null</warning>}");
    doTest(schema, "{\"first\": {}, \"second\": 44, <warning descr=\"Property 'other' is not allowed\">\"other\": 15</warning>}");
    doTest(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: [33, 44]\">12</warning>}");
  }

  public void testWithWaySelection() throws Exception {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    //doTest(schema, "{\"prop\": [{\"kilo\": 20}]}");
    //doTest(schema, "{\"prop\": 5}");
    doTest(schema, "{\"prop\": [{<warning descr=\"Property 'foxtrot' is not allowed\">\"foxtrot\": 15</warning>, \"kilo\": 20}]}");
  }

  public void testIntegerTypeWithMinMax() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax_schema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax.json"));
    doTest(schemaText, inputText);
  }

  public void testOneOf1() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOf1.json"));
    doTest(schemaText, inputText);
  }

  public void testOneOf2() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOf2.json"));
    doTest(schemaText, inputText);
  }

  public void testAnyOnePropertySelection() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOnePropertySelectionSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOnePropertySelection.json"));
    doTest(schemaText, inputText);
  }

  public void testAnyOneTypeSelection() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOneTypeSelectionSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOneTypeSelection.json"));
    doTest(schemaText, inputText);
  }

  public void testOneOfWithEmptyPropertyValue() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfWithEmptyPropertyValue.json"));
    doTest(schemaText, inputText);
  }

  public void testCycledSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledSchema.json"));
    doTest(schemaText, inputText);
  }

  public void testWithRootRefCycledSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledWithRootRefSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledWithRootRefSchema.json"));
    doTest(schemaText, inputText);
  }

  public void testCycledWithRootRefInNotSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledWithRootRefInNotSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledWithRootRefInNotSchema.json"));
    doTest(schemaText, inputText);
  }

  public void testPatternPropertiesHighlighting() throws Exception {
    @Language("JSON") final String schema = "{\n" +
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
                   "  \"Auto\": <warning descr=\"Type is not allowed. Expected: number.\">\"no\"</warning>,\n" +
                   "  \"BAe\": <warning descr=\"Type is not allowed. Expected: boolean.\">22</warning>,\n" +
                   "  \"Boloto\": <warning descr=\"Type is not allowed. Expected: boolean.\">2</warning>,\n" +
                   "  \"Cyan\": <warning descr=\"Value should be one of: [\\\"test\\\", \\\"em\\\"]\">\"me\"</warning>\n" +
                   "}");
  }

  public void testPatternPropertiesFromIssue() throws Exception {
    @Language("JSON") final String schema = "{\n" +
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
                   "  \"p1\": <warning descr=\"Type is not allowed. Expected: string.\">1</warning>,\n" +
                   "  \"p2\": \"3\",\n" +
                   "  \"a2\": \"auto!\",\n" +
                   "  \"a1\": <warning descr=\"Value should be one of: [\\\"auto!\\\"]\">\"moto!\"</warning>\n" +
                   "}");
  }

  public void testPatternForPropertyValue() throws Exception {
    @Language("JSON") final String schema = "{\n" +
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
    @Language("JSON") final String schema = "{\n" +
                                            "  \"properties\": {\n" +
                                            "    \"withPattern\": {\n" +
                                            "      \"pattern\": \"^\\\\d{4}\\\\-(0?[1-9]|1[012])\\\\-(0?[1-9]|[12][0-9]|3[01])$\"\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";
    @Language("JSON") final String correctText = "{\n" +
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
    @Language("JSON") final String schema = "{\n" +
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
                   "    \"a\": <warning descr=\"Type is not allowed. Expected: boolean.\">1</warning>," +
                   " \"b\":3, \"c\": 4, " +
                   "\"a\": <warning descr=\"Type is not allowed. Expected: boolean.\">5</warning>\n" +
                   "  }</warning>\n" +
                   "}");
  }

  public void testManyDuplicatesInArray() throws Exception {
    @Language("JSON") final String schema = "{\n" +
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
    @Language("JSON") final String schema = "{\n" +
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
    @Language("JSON") final String schema = "{\"properties\": {\n" +
                                            "    \"not_type\": { \"not\": { \"type\": \"string\" } }\n" +
                                            "  }}";
    doTest(schema, "{\"not_type\": <warning descr=\"Validates against 'not' schema\">\"wrong\"</warning>}");
  }

  public void testNotSchemaCombinedWithNormal() throws Exception {
    @Language("JSON") final String schema = "{\"properties\": {\n" +
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
    @Language("JSON") final String schema = "{\n" +
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
    @Language("JSON") final String schema = "{\n" +
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

  public void testArrayItemReference() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"items\": [\n" +
                                            "    {\n" +
                                            "      \"type\": \"integer\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"$ref\": \"#/items/0\"\n" +
                                            "    }\n" +
                                            "  ]\n" +
                                            "}";
    doTest(schema, "[1, 2]");
    doTest(schema, "[1, <warning>\"foo\"</warning>]");
  }

  public void testArrayReference() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"definitions\": {\n" +
                                            "    \"options\": {\n" +
                                            "      \"type\": \"array\",\n" +
                                            "      \"items\": {\n" +
                                            "        \"type\": \"number\"\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  },\n" +
                                            "  \"items\":{\n" +
                                            "      \"$ref\": \"#/definitions/options/items\"\n" +
                                            "    }\n" +
                                            "  \n" +
                                            "}";
    doTest(schema, "[2, 3 ,4]");
    doTest(schema, "[2, <warning>\"3\"</warning>]");
  }

  public void testSelfArrayReferenceDoesNotThrowSOE() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"items\": [\n" +
                                            "    {\n" +
                                            "      \"$ref\": \"#/items/0\"\n" +
                                            "    }\n" +
                                            "  ]\n" +
                                            "}";
    doTest(schema, "[]");
  }

  public void testValidateAdditionalItems() throws Exception {
    @Language("JSON") final String schema = "{\n" +
                                            "  \"definitions\": {\n" +
                                            "    \"options\": {\n" +
                                            "      \"type\": \"array\",\n" +
                                            "      \"items\": {\n" +
                                            "        \"type\": \"number\"\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  },\n" +
                                            "  \"items\": [\n" +
                                            "    {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"type\": \"boolean\"\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"additionalItems\": {\n" +
                                            "    \"$ref\": \"#/definitions/options/items\"\n" +
                                            "  }\n" +
                                            "}";
    doTest(schema, "[true, true]");
    doTest(schema, "[true, true, 1, 2, 3]");
    doTest(schema, "[true, true, 1, <warning>\"2\"</warning>]");
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

  public void testExclusiveMinMaxV6() throws Exception {
    @Language("JSON") String exclusiveMinSchema = "{\"properties\": {\"prop\": {\"exclusiveMinimum\": 3}}}";
    doTest(exclusiveMinSchema, "{\"prop\": <warning>2</warning>}");
    doTest(exclusiveMinSchema, "{\"prop\": <warning>3</warning>}");
    doTest(exclusiveMinSchema, "{\"prop\": 4}");

    @Language("JSON") String exclusiveMaxSchema = "{\"properties\": {\"prop\": {\"exclusiveMaximum\": 3}}}";
    doTest(exclusiveMaxSchema, "{\"prop\": 2}");
    doTest(exclusiveMaxSchema, "{\"prop\": <warning>3</warning>}");
    doTest(exclusiveMaxSchema, "{\"prop\": <warning>4</warning>}");
  }

  public void testPropertyNamesV6() throws Exception {
    doTest("{\"propertyNames\": {\"minLength\": 7}}", "{<warning>\"prop\"</warning>: 2}");
    doTest("{\"properties\": {\"prop\": {\"propertyNames\": {\"minLength\": 7}}}}", "{\"prop\": {<warning>\"qq\"</warning>: 7}}");
  }

  public void testContainsV6() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"contains\": {\"type\": \"number\"}}}}";
    doTest(schema, "{\"prop\": <warning>[{}, \"a\", true]</warning>}");
    doTest(schema, "{\"prop\": [{}, \"a\", 1, true]}");
  }

  public void testConstV6() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"const\": \"foo\"}}}";
    doTest(schema, "{\"prop\": <warning>\"a\"</warning>}");
    doTest(schema, "{\"prop\": <warning>5</warning>}");
    doTest(schema, "{\"prop\": \"foo\"}");
  }

  public void testIfThenElseV7() throws Exception {
    @Language("JSON") String schema = "{\n" +
                                      "  \"if\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"a\": {\n" +
                                      "        \"type\": \"string\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"a\"]\n" +
                                      "  },\n" +
                                      "  \"then\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"b\": {\n" +
                                      "        \"type\": \"number\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"b\"]\n" +
                                      "  },\n" +
                                      "  \"else\": {\n" +
                                      "    \"properties\": {\n" +
                                      "      \"c\": {\n" +
                                      "        \"type\": \"boolean\"\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    \"required\": [\"c\"]\n" +
                                      "  }\n" +
                                      "}";
    doTest(schema, "<warning>{}</warning>");
    doTest(schema, "{\"c\": <warning>5</warning>}");
    doTest(schema, "{\"c\": true}");
    doTest(schema, "<warning>{\"a\": 5, \"b\": 5}</warning>");
    doTest(schema, "{\"a\": 5, \"c\": <warning>5</warning>}");
    doTest(schema, "{\"a\": 5, \"c\": true}");
    doTest(schema, "<warning>{\"a\": \"a\", \"c\": true}</warning>");
    doTest(schema, "{\"a\": \"a\", \"b\": <warning>true</warning>}");
    doTest(schema, "{\"a\": \"a\", \"b\": 5}");
  }

  public void testNestedOneOf() throws Exception {
    @Language("JSON") String schema = "{\"type\":\"object\",\n" +
                                      "  \"oneOf\": [\n" +
                                      "    {\n" +
                                      "      \"properties\": {\n" +
                                      "        \"type\": {\n" +
                                      "          \"type\": \"string\",\n" +
                                      "          \"oneOf\": [\n" +
                                      "            {\n" +
                                      "              \"pattern\": \"(good)\"\n" +
                                      "            },\n" +
                                      "            {\n" +
                                      "              \"pattern\": \"(ok)\"\n" +
                                      "            }\n" +
                                      "          ]\n" +
                                      "        }\n" +
                                      "      }\n" +
                                      "    },\n" +
                                      "    {\n" +
                                      "      \"properties\": {\n" +
                                      "        \"type\": {\n" +
                                      "          \"type\": \"string\",\n" +
                                      "          \"pattern\": \"^(fine)\"\n" +
                                      "        },\n" +
                                      "        \"extra\": {\n" +
                                      "          \"type\": \"string\"\n" +
                                      "        }\n" +
                                      "      },\n" +
                                      "      \"required\": [\"type\", \"extra\"]\n" +
                                      "    }\n" +
                                      "  ]}";

    doTest(schema, "{\"type\": \"good\"}");
    doTest(schema, "{\"type\": \"ok\"}");
    doTest(schema, "{\"type\": <warning>\"doog\"</warning>}");
    doTest(schema, "{\"type\": <warning>\"ko\"</warning>}");
  }

  public void testArrayRefs() throws Exception {
    @Language("JSON") String schema = "{\n" +
                                      "  \"myDefs\": {\n" +
                                      "    \"myArray\": [\n" +
                                      "      {\n" +
                                      "        \"type\": \"number\"\n" +
                                      "      },\n" +
                                      "      {\n" +
                                      "        \"type\": \"string\"\n" +
                                      "      }\n" +
                                      "    ]\n" +
                                      "  },\n" +
                                      "  \"type\": \"array\",\n" +
                                      "  \"items\": [\n" +
                                      "    {\n" +
                                      "      \"$ref\": \"#/myDefs/myArray/0\"\n" +
                                      "    },\n" +
                                      "    {\n" +
                                      "      \"$ref\": \"#/myDefs/myArray/1\"\n" +
                                      "    }\n" +
                                      "  ]\n" +
                                      "}";

    doTest(schema, "[1, <warning>2</warning>]");
    doTest(schema, "[<warning>\"1\"</warning>, <warning>2</warning>]");
    doTest(schema, "[<warning>\"1\"</warning>, \"2\"]");
    doTest(schema, "[1, \"2\"]");
  }
}
