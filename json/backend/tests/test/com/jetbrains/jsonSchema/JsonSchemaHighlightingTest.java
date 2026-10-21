// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaDeprecationInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixtureKt.jsonSchemaHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JsonSchemaHighlightingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHighlightingTestFixture> schemaFixture =
    jsonSchemaHighlightingFixture(codeInsightFixture, "config.json", () -> new JsonSchemaComplianceInspection(), JsonLanguage.INSTANCE);

  protected CodeInsightTestFixture myFixture;
  protected JsonSchemaHighlightingTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
    Registry.get("json.schema.use.networknt.validation").setValue(false, myFixture.getTestRootDisposable());
  }

  @NotNull
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/jsonSchema/highlighting";
  }

  @Test
  public void testNumberMultipleWrong() {
    mySchemaFixture.doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}",
                           "{ \"prop\": <warning descr=\"Is not multiple of 2\">3</warning>}");
  }

  @Test
  public void testProviderReportsValidationErrors() {
    mySchemaFixture.configureInitially("{\"properties\": {\"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}",
                                       "{\"prop\": <error descr=\"Is not multiple of 2\">3</error>}", "json");
    var schemaFile = myFixture.findFileInTempDir("json_schema_test/schema.json");
    assertNotNull(schemaFile);
    JsonSchemaTestServiceImpl.setProvider(new JsonSchemaTestProvider(schemaFile, mySchemaFixture.getAvailabilityPredicate(),
                                                                    ProblemHighlightType.GENERIC_ERROR));
    JsonSchemaService.Impl.get(myFixture.getProject()).reset();
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testHighlightTypeComesFromProviderThatMatchesFile() {
    mySchemaFixture.configureInitially("{\"properties\": {\"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}",
                                       "{\"prop\": <warning descr=\"Is not multiple of 2\">3</warning>}", "json");
    var schemaFile = myFixture.findFileInTempDir("json_schema_test/schema.json");
    assertNotNull(schemaFile);
    JsonSchemaTestServiceImpl.setProviders(new JsonSchemaTestProvider(schemaFile, _ -> false, ProblemHighlightType.GENERIC_ERROR),
                                           new JsonSchemaTestProvider(schemaFile, mySchemaFixture.getAvailabilityPredicate()));
    JsonSchemaService.Impl.get(myFixture.getProject()).reset();
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testNumberMultipleCorrect() {
    mySchemaFixture.doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}", "{ \"prop\": 4}");
  }

  @Test
  public void testNumberMinMax() {
    mySchemaFixture.doTest("""
                             { "properties": { "prop": {
                               "type": "number",
                               "minimum": 0,
                               "maximum": 100,
                               "exclusiveMaximum": true
                             }}}""", "{ \"prop\": 14}");
  }

  @Test
  public void testEnum() {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"enum\": [1,2,3,\"18\"]}}}";
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: 1, 2, 3, \\\"18\\\"\">18</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": 2}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"18\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: 1, 2, 3, \\\"18\\\"\">\"2\"</warning>}");
  }

  @Test
  public void testSimpleString() {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"minLength\": 2, \"maxLength\": 3}}}";
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"String is shorter than 2\">\"s\"</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"sh\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"sho\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"String is longer than 3\">\"shor\"</warning>}");
  }

  @Test
  public void testArray() {
    @Language("JSON") final String schema = schema("""
                                                     {
                                                       "type": "array",
                                                       "items": {
                                                         "type": "number", "minimum": 18  }
                                                     }""");
    mySchemaFixture.doTest(schema, "{\"prop\": [101, 102]}");
    mySchemaFixture.doTest(schema, "{\"prop\": [<warning descr=\"Less than the minimum 18\">16</warning>]}");
    mySchemaFixture.doTest(schema, "{\"prop\": [<warning descr=\"Incompatible types.\n Required: number. Actual: string.\">\"test\"</warning>]}");
  }

  @Test
  public void testTopLevelArray() {
    @Language("JSON") final String schema = """
      {
        "type": "array",
        "items": {
          "type": "number", "minimum": 18  }
      }""";
    mySchemaFixture.doTest(schema, "[101, 102]");
  }

  @Test
  public void testTopLevelObjectArray() {
    @Language("JSON") final String schema = """
      {
        "type": "array",
        "items": {
          "type": "object", "properties": {"a": {"type": "number"}}  }
      }""";
    mySchemaFixture.doTest(schema, "[{\"a\": <warning descr=\"Incompatible types.\n Required: number. Actual: boolean.\">true</warning>}]");
    mySchemaFixture.doTest(schema, "[{\"a\": 18}]");
  }

  @Test
  public void testArrayTuples1() {
    @Language("JSON") final String schema = schema("""
                                                     {
                                                       "type": "array",
                                                       "items": [{
                                                         "type": "number", "minimum": 18  }, {"type" : "string"}]
                                                     }""");
    mySchemaFixture.doTest(schema, "{\"prop\": [101, <warning descr=\"Incompatible types.\n Required: string. Actual: integer.\">102</warning>]}");
    mySchemaFixture.doTest(schema, "{\"prop\": [101, \"102\"]}");
    mySchemaFixture.doTest(schema, "{\"prop\": [101, \"102\", \"additional\"]}");
  }

  @Test
  public void testArrayTuples2() {
    @Language("JSON") final String schema2 = schema("""
                                                      {
                                                        "type": "array",
                                                        "items": [{
                                                          "type": "number", "minimum": 18  }, {"type" : "string"}],
                                                      "additionalItems": false}""");
    mySchemaFixture.doTest(schema2, "{\"prop\": [101, \"102\", <warning descr=\"Additional items are not allowed\">\"additional\"</warning>]}");
  }

  @Test
  public void testArrayLength() {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Array is shorter than 2\">[]</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": [1,2]}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Array is longer than 3\">[1,2,3,4]</warning>}");
  }

  @Test
  public void testArrayUnique() {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    mySchemaFixture.doTest(schema, "{\"prop\": [1,2]}");
    mySchemaFixture.doTest(schema, "{\"prop\": [<warning descr=\"Item is not unique\">1</warning>,2, \"test\", <warning descr=\"Item is not unique\">1</warning>]}");
  }

  @Test
  public void testMetadataIsOk() {
    @Language("JSON") final String schema = """
      {
        "title" : "Match anything",
        "description" : "This is a schema that matches anything.",
        "default" : "Default value"
      }""";
    mySchemaFixture.doTest(schema, "{\"anything\": 1}");
  }

  @Test
  public void testRequiredField() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    mySchemaFixture.doTest(schema, "{\"a\": 11}");
    mySchemaFixture.doTest(schema, "{\"a\": 1, \"b\": true}");
    mySchemaFixture.doTest(schema, "{<warning descr=\"Missing required property 'a'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"b\": \"alarm\"</warning>}");
  }

  @Test
  public void testInnerRequired() {
    @Language("JSON") final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    mySchemaFixture.doTest(schema, "{\"prop\": {\"a\": 11}}");
    mySchemaFixture.doTest(schema, "{\"prop\": {\"a\": 1, \"b\": true}}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>}");
  }

  @Test
  public void testUseDefinition() {
    @Language("JSON") final String schema = "{\"definitions\": {\"address\": {\"type\": \"object\", \"properties\": {\"street\": {\"type\": \"string\"}," +
                                            "\"house\": {\"type\": \"integer\"}}}}," +
                                            "\"type\": \"object\", \"properties\": {" +
                                            "\"home\": {\"$ref\": \"#/definitions/address\"}, " +
                                            "\"office\": {\"$ref\": \"#/definitions/address\"}" +
                                            "}}";
    mySchemaFixture.doTest(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": 11}}");
    mySchemaFixture.doTest(schema, """
      {"home": {"street": "Broadway", "house": <warning descr="Incompatible types.
       Required: integer. Actual: string.">"unknown"</warning>},"office": {"street": <warning descr="Incompatible types.
       Required: string. Actual: integer.">5</warning>}}""");
  }

  @Test
  public void testAdditionalPropertiesAllowed() {
    @Language("JSON") final String schema = schema("{}");
    mySchemaFixture.doTest(schema, "{\"prop\": {}, \"someStuff\": 20}");
  }

  @Test
  public void testAdditionalPropertiesDisabled() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    mySchemaFixture.doTest(schema, "{\"prop\": {}, <warning descr=\"Property 'someStuff' is not allowed\">\"someStuff\"</warning>: 20}");
  }

  @Test
  public void testAdditionalPropertiesSchema() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                                            "\"additionalProperties\": {\"type\": \"string\"}}";
    mySchemaFixture.doTest(schema, "{\"a\" : 18, \"b\": \"wall\", \"c\": <warning descr=\"Incompatible types.\n Required: string. Actual: integer.\">11</warning>}");
  }

  @Test
  public void testMinMaxProperties() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"minProperties\": 1, \"maxProperties\": 2}";
    mySchemaFixture.doTest(schema, "<warning descr=\"Number of properties is less than 1\">{}</warning>");
    mySchemaFixture.doTest(schema, "{\"a\": 1}");
    mySchemaFixture.doTest(schema, "{<warning descr=\"Number of properties is greater than 2\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"a\": 1</warning>, \"b\": 22, \"c\": 33}");
  }

  @Test
  public void testOneOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"abc\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": true}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Incompatible types.\n Required one of: boolean, string. Actual: integer.\">11</warning>}");
  }

  @Test
  public void testOneOfForTwoMatches() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"b\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"c\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Validates to more than one variant\">\"a\"</warning>}");
  }

  @Test
  public void testOneOfSelectError() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {"type": "string",
                               "enum": [
                                 "off", "warn", "error"
                               ]}""");
    subSchemas.add("{\"type\": \"integer\"}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"off\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": 12}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: \\\"off\\\", \\\"warn\\\", \\\"error\\\"\">\"wrong\"</warning>}");
  }

  @Test
  public void testAnyOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"b\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"c\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"a\"}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: \\\"a\\\", \\\"b\\\", \\\"c\\\"\">\"d\"</warning>}");
  }

  @Test
  public void testAllOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"integer\", \"multipleOf\": 2}");
    subSchemas.add("{\"enum\": [1,2,3]}");
    @Language("JSON") final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Is not multiple of 2\">1</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: 1, 2, 3\">4</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": 2}");
  }

  @Test
  public void testObjectInArray() {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":{}, \"innerValue\":{}" +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    mySchemaFixture.doTest(schema, "{\"prop\": [{\"innerType\":{}, <warning descr=\"Property 'alien' is not allowed\">\"alien\"</warning>:{}}]}");
  }

  @Test
  public void testObjectDeeperInArray() {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":" + innerTypeSchema +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    mySchemaFixture.doTest(schema,
                           "{\"prop\": [{\"innerType\":{\"only\": true, <warning descr=\"Property 'hidden' is not allowed\">\"hidden\"</warning>: false}}]}");
  }

  @Test
  public void testInnerObjectPropValueInArray() {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    mySchemaFixture.doTest(schema, "{\"prop\": [1,3]}");
    mySchemaFixture.doTest(schema, "{\"prop\": [<warning descr=\"Value should be one of: 1, 2, 3\">\"out\"</warning>]}");
  }

  @Test
  public void testAllOfProperties() {
    @Language("JSON") final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                                                                                " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
    mySchemaFixture.doTest(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: 33, 44\">null</warning>}");
    mySchemaFixture.doTest(schema, "{\"first\": {}, \"second\": 44, <warning descr=\"Property 'other' is not allowed\">\"other\"</warning>: 15}");
    mySchemaFixture.doTest(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: 33, 44\">12</warning>}");
  }

  @Test
  public void testWithWaySelection() {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    //doTest(schema, "{\"prop\": [{\"kilo\": 20}]}");
    //doTest(schema, "{\"prop\": 5}");
    mySchemaFixture.doTest(schema, "{\"prop\": [{<warning descr=\"Property 'foxtrot' is not allowed\">\"foxtrot\"</warning>: 15, \"kilo\": 20}]}");
  }

  @Test
  public void testIntegerTypeWithMinMax() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax_schema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/integerTypeWithMinMax.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testOneOf1() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOf1.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testOneOf2() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOf2.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testAnyOnePropertySelection() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOnePropertySelectionSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOnePropertySelection.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testAnyOneTypeSelection() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOneTypeSelectionSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOneTypeSelection.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testOneOfWithEmptyPropertyValue() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfWithEmptyPropertyValue.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testCycledSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledSchema.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testWithRootRefCycledSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledWithRootRefSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledWithRootRefSchema.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testCycledWithRootRefInNotSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/cycledWithRootRefInNotSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/testCycledWithRootRefInNotSchema.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testPatternPropertiesHighlighting() {
    @Language("JSON") final String schema = """
      {
        "patternProperties": {
          "^A" : {
            "type": "number"
          },
          "B": {
            "type": "boolean"
          },
          "C": {
            "enum": ["test", "em"]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      {
        "Abezjana": 2,
        "Auto": <warning descr="Incompatible types.
       Required: number. Actual: string.">"no"</warning>,
        "BAe": <warning descr="Incompatible types.
       Required: boolean. Actual: integer.">22</warning>,
        "Boloto": <warning descr="Incompatible types.
       Required: boolean. Actual: integer.">2</warning>,
        "Cyan": <warning descr="Value should be one of: \\"test\\", \\"em\\"">"me"</warning>
      }""");
  }

  @Test
  public void testPatternPropertiesFromIssue() {
    @Language("JSON") final String schema = """
      {
        "type": "object",
        "additionalProperties": false,
        "patternProperties": {
          "p[0-9]": {
            "type": "string"
          },
          "a[0-9]": {
            "enum": ["auto!"]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      {
        "p1": <warning descr="Incompatible types.
       Required: string. Actual: integer.">1</warning>,
        "p2": "3",
        "a2": "auto!",
        "a1": <warning descr="Value should be one of: \\"auto!\\"">"moto!"</warning>
      }""");
  }

  @Test
  public void testPatternForPropertyValue() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "p[0-9]"
          }
        }
      }""";
    final String correctText = """
      {
        "withPattern": "p1"
      }""";
    final String wrongText = """
      {
        "withPattern": <warning descr="String violates the pattern: 'p[0-9]'">"wrong"</warning>
      }""";
    mySchemaFixture.doTest(schema, correctText);
    mySchemaFixture.doTest(schema, wrongText);
  }

  @Test
  public void testPatternWithSpecialEscapedSymbols() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^\\\\d{4}\\\\-(0?[1-9]|1[012])\\\\-(0?[1-9]|[12][0-9]|3[01])$"
          }
        }
      }""";
    @Language("JSON") final String correctText = """
      {
        "withPattern": "1234-11-11"
      }""";
    final String wrongText = """
      {
        "withPattern": <warning descr="String violates the pattern: '^\\d{4}\\-(0?[1-9]|1[012])\\-(0?[1-9]|[12][0-9]|3[01])$'">"wrong"</warning>
      }""";
    mySchemaFixture.doTest(schema, correctText);
    mySchemaFixture.doTest(schema, wrongText);
  }

  @Test
  public void testRootObjectRedefinedAdditionalPropertiesForbidden() {
    mySchemaFixture.doTest(rootObjectRedefinedSchema(), "{<warning descr=\"Property 'a' is not allowed\">\"a\"</warning>: true," +
                                                        "\"r1\": \"allowed!\"}");
  }

  @Test
  public void testNumberOfSameNamedPropertiesCorrectlyChecked() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "size": {
            "type": "object",
            "minProperties": 2,
            "maxProperties": 3,
            "properties": {
              "a": {
                "type": "boolean"
              }
            }
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      {
        "size": {
          "a": <warning descr="Incompatible types.
       Required: boolean. Actual: integer.">1</warning>, "b":3, "c": 4, "a": <warning descr="Incompatible types.
       Required: boolean. Actual: integer.">5</warning>
        }
      }""");
    mySchemaFixture.doTest(schema, """
      {
        "size": <warning descr="Number of properties is greater than 3">{
          "a": true, "b":3, "c": 4, "a": false
        }</warning>
      }""");
  }

  @Test
  public void testManyDuplicatesInArray() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "array":{
            "type": "array",
            "uniqueItems": true
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "{\"array\": [<warning descr=\"Item is not unique\">1</warning>," +
                                   "<warning descr=\"Item is not unique\">1</warning>," +
                                   "<warning descr=\"Item is not unique\">1</warning>," +
                                   "<warning descr=\"Item is not unique\">2</warning>," +
                                   "<warning descr=\"Item is not unique\">2</warning>," +
                                   "5," +
                                   "<warning descr=\"Item is not unique\">3</warning>," +
                                   "<warning descr=\"Item is not unique\">3</warning>]}");
  }

  @Test
  public void testPropertyValueAlsoHighlightedIfPatternIsInvalid() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^[]$"
          }
        }
      }""";
    final String text = """
      {"withPattern": <warning descr="Cannot check the string by pattern because of an error: Unclosed character class near index 3
      ^[]$
         ^">"(124)555-4216"</warning>}""";
    mySchemaFixture.doTest(schema, text);
  }

  @Test
  public void testNotSchema() {
    @Language("JSON") final String schema = """
      {"properties": {
          "not_type": { "not": { "type": "string" } }
        }}""";
    mySchemaFixture.doTest(schema, "{\"not_type\": <warning descr=\"Validates against 'not' schema\">\"wrong\"</warning>}");
  }

  @Test
  public void testNotSchemaCombinedWithNormal() {
    @Language("JSON") final String schema = """
      {"properties": {
          "not_type": {
            "pattern": "^[a-z]*[0-5]*$",
            "not": { "pattern": "^[a-z]{1}[0-5]$" }
          }
        }}""";
    mySchemaFixture.doTest(schema, "{\"not_type\": \"va4\"}");
    mySchemaFixture.doTest(schema, "{\"not_type\": <warning descr=\"Validates against 'not' schema\">\"a4\"</warning>}");
    mySchemaFixture.doTest(schema, "{\"not_type\": <warning descr=\"String violates the pattern: '^[a-z]*[0-5]*$'\">\"4a4\"</warning>}");
  }

  @Test
  public void testDoNotMarkOneOfThatDiffersWithFormat() {
    @Language("JSON") final String schema = """
      {

        "properties": {
          "withFormat": {
            "type": "string",      "oneOf": [
              {
                "format":"hostname"
              },
              {
                "format": "ip4"
              }
            ]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "{\"withFormat\": \"localhost\"}");
  }

  @Test
  public void testAcceptSchemaWithoutType() {
    @Language("JSON") final String schema = """
      {

        "properties": {
          "withFormat": {
            "oneOf": [
              {
                "format":"hostname"
              },
              {
                "format": "ip4"
              }
            ]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "{\"withFormat\": \"localhost\"}");
  }

  @Test
  public void testArrayItemReference() {
    @Language("JSON") final String schema = """
      {
        "items": [
          {
            "type": "integer"
          },
          {
            "$ref": "#/items/0"
          }
        ]
      }""";
    mySchemaFixture.doTest(schema, "[1, 2]");
    mySchemaFixture.doTest(schema, "[1, <warning>\"foo\"</warning>]");
  }

  @Test
  public void testArrayReference() {
    @Language("JSON") final String schema = """
      {
        "definitions": {
          "options": {
            "type": "array",
            "items": {
              "type": "number"
            }
          }
        },
        "items":{
            "$ref": "#/definitions/options/items"
          }
       \s
      }""";
    mySchemaFixture.doTest(schema, "[2, 3 ,4]");
    mySchemaFixture.doTest(schema, "[2, <warning>\"3\"</warning>]");
  }

  @Test
  public void testSelfArrayReferenceDoesNotThrowSOE() {
    @Language("JSON") final String schema = """
      {
        "items": [
          {
            "$ref": "#/items/0"
          }
        ]
      }""";
    mySchemaFixture.doTest(schema, "[]");
  }

  @Test
  public void testValidateAdditionalItems() {
    @Language("JSON") final String schema = """
      {
        "definitions": {
          "options": {
            "type": "array",
            "items": {
              "type": "number"
            }
          }
        },
        "items": [
          {
            "type": "boolean"
          },
          {
            "type": "boolean"
          }
        ],
        "additionalItems": {
          "$ref": "#/definitions/options/items"
        }
      }""";
    mySchemaFixture.doTest(schema, "[true, true]");
    mySchemaFixture.doTest(schema, "[true, true, 1, 2, 3]");
    mySchemaFixture.doTest(schema, "[true, true, 1, <warning>\"2\"</warning>]");
  }

  public static String rootObjectRedefinedSchema() {
    return """
      {
        "$schema": "http://json-schema.org/draft-04/schema#",
        "type": "object",
        "$ref" : "#/definitions/root",
        "definitions": {
          "root" : {
            "type": "object",
            "additionalProperties": false,
            "properties": {
              "r1": {
                "type": "string"
              },
              "r2": {
                "type": "string"
              }
            }
          }
        }
      }
      """;
  }

  static String schema(final String s) {
    return "{\"type\": \"object\", \"properties\": {\"prop\": " + s + "}}";
  }

  @Test
  public void testExclusiveMinMaxV6_1() {
    @Language("JSON") String exclusiveMinSchema = "{\"$schema\": \"http://json-schema.org/draft-06/schema#\", \"properties\": {\"prop\": {\"exclusiveMinimum\": 3}}}";
    mySchemaFixture.doTest(exclusiveMinSchema, "{\"prop\": <warning>2</warning>}");
    mySchemaFixture.doTest(exclusiveMinSchema, "{\"prop\": <warning>3</warning>}");
    mySchemaFixture.doTest(exclusiveMinSchema, "{\"prop\": 4}");
  }

  @Test
  public void testExclusiveMinMaxV6_2() {
    @Language("JSON") String exclusiveMaxSchema = "{\"$schema\": \"http://json-schema.org/draft-06/schema#\", \"properties\": {\"prop\": {\"exclusiveMaximum\": 3}}}";
    mySchemaFixture.doTest(exclusiveMaxSchema, "{\"prop\": 2}");
    mySchemaFixture.doTest(exclusiveMaxSchema, "{\"prop\": <warning>3</warning>}");
    mySchemaFixture.doTest(exclusiveMaxSchema, "{\"prop\": <warning>4</warning>}");
  }

  @Test
  public void testPropertyNamesV6_1() {
    mySchemaFixture.doTest("{\"$schema\": \"http://json-schema.org/draft-06/schema#\", \"propertyNames\": {\"minLength\": 7}}", "{<warning>\"prop\"</warning>: 2}");
  }

  @Test
  public void testPropertyNamesV6_2() {
    mySchemaFixture.doTest("{\"$schema\": \"http://json-schema.org/draft-06/schema#\", \"properties\": {\"prop\": {\"propertyNames\": {\"minLength\": 7}}}}", "{\"prop\": {<warning>\"qq\"</warning>: 7}}");
  }

  @Test
  public void testContainsV6() {
    @Language("JSON") String schema = "{\"$schema\": \"http://json-schema.org/draft-06/schema#\", \"properties\": {\"prop\": {\"type\": \"array\", \"contains\": {\"type\": \"number\"}}}}";
    mySchemaFixture.doTest(schema, "{\"prop\": <warning>[{}, \"a\", true]</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": [{}, \"a\", 1, true]}");
  }

  @Test
  public void testConstV6() {
    @Language("JSON") String schema = "{\"$schema\": \"http://json-schema.org/draft-06/schema#\", \"properties\": {\"prop\": {\"type\": \"string\", \"const\": \"foo\"}}}";
    mySchemaFixture.doTest(schema, "{\"prop\": <warning>\"a\"</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": <warning>5</warning>}");
    mySchemaFixture.doTest(schema, "{\"prop\": \"foo\"}");
  }

  @Test
  public void testIfThenElseV7() {
    @Language("JSON") String schema = """
      {
        "$schema": "http://json-schema.org/draft-07/schema#",
        "if": {
          "properties": {
            "a": {
              "type": "string"
            }
          },
          "required": ["a"]
        },
        "then": {
          "properties": {
            "b": {
              "type": "number"
            }
          },
          "required": ["b"]
        },
        "else": {
          "properties": {
            "c": {
              "type": "boolean"
            }
          },
          "required": ["c"]
        }
      }""";
    mySchemaFixture.doTest(schema, "<warning>{}</warning>");
    mySchemaFixture.doTest(schema, "{\"c\": <warning descr=\"Incompatible types.\n Required: boolean. Actual: integer.\">5</warning>}");
    mySchemaFixture.doTest(schema, "{\"c\": true}");
    mySchemaFixture.doTest(schema, "{<warning descr=\"Missing required property 'c'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"a\": 5</warning>, \"b\": 5}");
    mySchemaFixture.doTest(schema, "{\"a\": 5, \"c\": <warning>5</warning>}");
    mySchemaFixture.doTest(schema, "{\"a\": 5, \"c\": true}");
    mySchemaFixture.doTest(schema, "{<warning descr=\"Missing required property 'b'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"a\": \"a\"</warning>, \"c\": true}");
    mySchemaFixture.doTest(schema, "{\"a\": \"a\", \"b\": <warning>true</warning>}");
    mySchemaFixture.doTest(schema, "{\"a\": \"a\", \"b\": 5}");
  }

  @Test
  public void testNestedOneOf() {
    @Language("JSON") String schema = """
      {"type":"object",
        "oneOf": [
          {
            "properties": {
              "type": {
                "type": "string",
                "oneOf": [
                  {
                    "pattern": "(good)"
                  },
                  {
                    "pattern": "(ok)"
                  }
                ]
              }
            }
          },
          {
            "properties": {
              "type": {
                "type": "string",
                "pattern": "^(fine)"
              },
              "extra": {
                "type": "string"
              }
            },
            "required": ["type", "extra"]
          }
        ]}""";

    mySchemaFixture.doTest(schema, "{\"type\": \"good\"}");
    mySchemaFixture.doTest(schema, "{\"type\": \"ok\"}");
    mySchemaFixture.doTest(schema, "{\"type\": <warning>\"doog\"</warning>}");
    mySchemaFixture.doTest(schema, "{\"type\": <warning>\"ko\"</warning>}");
  }

  @Test
  public void testArrayRefs() {
    @Language("JSON") String schema = """
      {
        "myDefs": {
          "myArray": [
            {
              "type": "number"
            },
            {
              "type": "string"
            }
          ]
        },
        "type": "array",
        "items": [
          {
            "$ref": "#/myDefs/myArray/0"
          },
          {
            "$ref": "#/myDefs/myArray/1"
          }
        ]
      }""";

    mySchemaFixture.doTest(schema, "[1, <warning>2</warning>]");
    mySchemaFixture.doTest(schema, "[<warning>\"1\"</warning>, <warning>2</warning>]");
    mySchemaFixture.doTest(schema, "[<warning>\"1\"</warning>, \"2\"]");
    mySchemaFixture.doTest(schema, "[1, \"2\"]");
  }

  @Test
  public void testOneOfInsideAllOf() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "foo": {
            "allOf": [
              {
                "type": "object"
              }, {
                "oneOf": [
                  {
                    "type": "object",
                    "properties": {
                      "provider": {
                        "enum": ["script"]
                      },
                      "foo21": {}
                    }
                  },
                  {
                    "type": "object",
                    "properties": {
                      "provider": {
                        "enum": ["npm"]
                      },
                      "foo11": {}
                    }
                  }
                ]
              }
            ]
          }
        }
      }""";

    mySchemaFixture.doTest(schema, """
      {
        "foo": {
          "provider": "npm"
        }
      }""");

    mySchemaFixture.doTest(schema, """
      {
        "foo": {
          "provider": "script"
        }
      }""");

    mySchemaFixture.doTest(schema, """
      {
        "foo": {
          "provider": <warning>"etwasanderes"</warning>
        }
      }""");
  }

  @Test
  public void testOneOfBestChoiceSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfBestChoiceSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      {
        "results": [
          <warning descr="Missing required properties 'dateOfBirth', 'name'">{
            "type": "person"
          }</warning>
        ]
      }""");
  }

  @Test
  public void testAnyOfBestChoiceSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOfBestChoiceSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      [
        {
          "directory": "/test",
          "arguments": [
            "a"
          ],
          "file": <warning>""</warning>
        }
      ]\s""");
  }

  @Test
  public void testComplexOneOfSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/complexOneOfSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      {
          "indentation": "tab"
        }""");
    mySchemaFixture.doTest(schemaText, """
      {
          "indentation": <warning>"ttab"</warning>
        }""");
  }

  @Test
  public void testEnumCasing() {
    @Language("JSON") String schema = """
      {
        "type": "object",

        "properties": {
          "name": { "type": "string", "enum": ["aa", "bb"] }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      {
        "name": "aa"
      }""");
    mySchemaFixture.doTest(schema, """
      {
        "name": <warning>"aA"</warning>
      }""");
  }

  @Test
  public void testEnumArrayValue() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "foo": {
            "enum": [ [{"x": 5}, [true], "q"] ]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>5</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>[ ]</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>[{\"x\": 5}]</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>[{\"x\": 5}, true]</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>[{\"x\": 5}, [true]]</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": [  { \"x\"   :   5 }  ,  [ true ]  , \"q\"  ]}");
  }

  @Test
  public void testEnumObjectValue() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "foo": {
            "enum": [ {"x": 5} ]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>{}</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>{\"x\": 4}</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": <warning>{\"x\": true}</warning>}");
    mySchemaFixture.doTest(schema, "{\"foo\": { \r  \"x\"  : \t  5 \n  }}");
  }

  @Test
  public void testIntersectingHighlightingRanges() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/avroSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      {
        <warning descr="Missing required property 'items'" textAttributesKey="WARNING_ATTRIBUTES">"type": "array"</warning>
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "type": <warning descr="Value should be one of: \\"array\\", \\"enum\\", \\"fixed\\", \\"map\\", \\"record\\"">"array2"</warning>
      }""");
  }

  @Test
  public void testMissingMultipleAltPropertySets() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/avroSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      <warning descr="One of the following property sets is required: properties 'type' = record, 'fields', 'name', properties 'type' = enum, 'name', 'symbols', properties 'type' = array, 'items', properties 'type' = map, 'values', or properties 'type' = fixed, 'name', 'size'">{
       \s
      }</warning>""");
  }

  @Test
  public void testValidateEnumVsPattern() {
    mySchemaFixture.doTest("""
                             {
                               "oneOf": [
                                     {
                                         "properties": {
                                             "type": {
                                                 "enum": ["library"],
                                                 "pattern": ".*"
                                             }
                                         },
                                         "required": ["type", "name", "description"]
                                     },
                                     {
                                         "properties": {
                                             "type": {
                                                 "not": {
                                                     "enum": ["library"]
                                                 }
                                             }
                                         }
                                     }
                                 ]
                             }""", """
                             {
                               "type": "project",
                               "name": "asd",
                               "description": "asdasdqwdqw"
                             }""");
  }

  @Test
  public void testJsonPointerEscapes() {
    mySchemaFixture.doTest("""
                             {
                               "properties": {
                                 "q~q/q": {
                                   "type": "string"
                                 },
                                 "a": {
                                   "$ref": "#/properties/q~0q~1q"
                                 }
                               }
                             }""", """
                             {
                               "a": <warning>1</warning>
                             }""");
  }

  @Test
  public void testOneOfMultipleBranches() {
    mySchemaFixture.doTest("""
                             {
                             \t"$schema": "http://json-schema.org/draft-04/schema#",

                             \t"type": "object",
                             \t"oneOf": [
                             \t\t{
                             \t\t\t"properties": {
                             \t\t\t\t"startTime": {
                             \t\t\t\t\t"type": "string"
                             \t\t\t\t}
                             \t\t\t}
                             \t\t},
                             \t\t{
                             \t\t\t"properties": {
                             \t\t\t\t"startTime": {
                             \t\t\t\t\t"type": "number"
                             \t\t\t\t}
                             \t\t\t}
                             \t\t}
                             \t]
                             }""", """
                             {
                               "startTime": <warning descr="Incompatible types.
                             Required one of: number, string. Actual: null.">null</warning>
                             }""");
  }

  @Test
  public void testReferenceById() {
    mySchemaFixture.doTest("""
                             {
                               "$schema": "https://json-schema.org/draft-07/schema",
                               "type": "object",

                               "properties": {
                                 "a": {
                                   "$id": "#aa",
                                   "type": "object"
                                 }
                               },
                               "patternProperties": {
                                 "aa": {
                                   "type": "object"
                                 },
                                 "bb": {
                                   "$ref": "#aa"
                                 }
                               }
                             }""", """
                             {
                               "aa": {
                                 "type": "string"
                               },
                               "bb": <warning>578</warning>
                             }

                             """);
  }

  @Test
  public void testComplicatedConditions() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/complicatedConditions_schema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/complicatedConditions.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testExoticProps() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/exoticPropsSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/exoticProps.json"));
    mySchemaFixture.doTest(schemaText, inputText);
  }

  @Test
  public void testLargeInt() {
    // currently we limit it by Java Long range, should be sufficient as per RFC 7159
    mySchemaFixture.doTest("""
                             {
                               "properties": {
                                 "x": {
                                   "type": "integer"
                                 }
                               }
                             }""", """
                             {
                               "x": 9223372036854775807
                             }""");
  }

  @Test
  public void testMultipleIfThenElse() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/multipleIfThenElseSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      {
        "street_address": "1600 Pennsylvania Avenue NW",
        "country": "United States of America",
        "postal_code": "20500"
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "street_address": "1600 Pennsylvania Avenue NW",
        "country": "Netherlands",
        "postal_code": <warning descr="String violates the pattern: '[0-9]{4} [A-Z]{2}'">"20500"</warning>
      }""");
  }

  @Test
  public void testDeprecation() throws IOException {
    myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/deprecation.json"));
    mySchemaFixture.configureInitially(schemaText,
                                       """
                                           {
                                             "framework": "vue",
                                             <weak_warning descr="Property 'directProperty' is deprecated: Baz">"directProperty"</weak_warning>: <warning descr="Incompatible types.
                                          Required: number. Actual: string.">"foo"</warning>,
                                             <weak_warning descr="Property 'vue-modifiers' is deprecated: Contribute Vue directives to /contributions/html/vue-directives">"vue-modifiers"</weak_warning>: [{
                                               "name": "foo"
                                             }],
                                             <weak_warning descr="Property 'description-markup' is deprecated: Use top-level property.">"description-markup"</weak_warning>: "html"
                                           }\
                                         """, "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testDeprecationFromMatchingAnyOfBranchOnly() {
    myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
    mySchemaFixture.configureInitially("""
                         {
                           "type": "object",
                           "properties": {
                             "steps": {
                               "type": "array",
                               "items": {
                                 "type": "object",
                                 "properties": {
                                   "task": {
                                     "anyOf": [
                                       {
                                         "enum": ["Docker@2"]
                                       },
                                       {
                                         "deprecationMessage": "Deprecated task",
                                         "enum": ["AzurePowerShell@5"]
                                       }
                                     ]
                                   }
                                 }
                               }
                             }
                           }
                         }""",
                       """
                         {
                           "steps": [
                             {
                               "task": "Docker@2"
                             },
                             {
                               <weak_warning descr="Property 'task' is deprecated: Deprecated task">"task"</weak_warning>: "AzurePowerShell@5"
                             }
                           ]
                         }""", "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testDeprecationFromMatchingAnyOfBranchWithOwnerEnum() {
    myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
    mySchemaFixture.configureInitially("""
                         {
                           "type": "object",
                           "properties": {
                             "steps": {
                               "type": "array",
                               "items": {
                                 "type": "object",
                                 "properties": {
                                   "task": {
                                     "enum": ["Docker@2", "AzurePowerShell@5"],
                                     "anyOf": [
                                       {
                                         "enum": ["Docker@2"]
                                       },
                                       {
                                         "deprecationMessage": "Deprecated task",
                                         "enum": ["AzurePowerShell@5"]
                                       }
                                     ]
                                   }
                                 }
                               }
                             }
                           }
                         }""",
                       """
                         {
                           "steps": [
                             {
                               "task": "Docker@2"
                             },
                             {
                               <weak_warning descr="Property 'task' is deprecated: Deprecated task">"task"</weak_warning>: "AzurePowerShell@5"
                             }
                           ]
                         }""", "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testDirectDeprecationUnderInheritedOneOf() {
    myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
    mySchemaFixture.configureInitially("""
                         {
                           "type": "object",
                           "properties": {
                             "obj": {
                               "type": "object",
                               "properties": {
                                 "old": {
                                   "deprecationMessage": "Old property",
                                   "type": "number"
                                 }
                               },
                               "if": {},
                               "then": {
                                 "oneOf": [
                                   {
                                     "required": ["a"]
                                   },
                                   {
                                     "not": {
                                       "required": ["a"]
                                     }
                                   }
                                 ]
                               }
                             }
                           }
                         }""",
                       """
                         {
                           "obj": {
                             <weak_warning descr="Property 'old' is deprecated: Old property">"old"</weak_warning>: <warning descr="Incompatible types.
                          Required: number. Actual: string.">"foo"</warning>
                           }
                         }""", "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testDirectDeprecationWithAllOfAnyOf() {
    myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
    mySchemaFixture.configureInitially("""
                         {
                           "type": "object",
                           "properties": {
                             "old": {
                               "deprecationMessage": "Old property",
                               "allOf": [
                                 {
                                   "anyOf": [
                                     {
                                       "type": "string"
                                     },
                                     {
                                       "type": "number"
                                     }
                                   ]
                                 }
                               ]
                             }
                           }
                         }""",
                       """
                         {
                           <weak_warning descr="Property 'old' is deprecated: Old property">"old"</weak_warning>: <warning descr="Incompatible types.
                          Required one of: number, string. Actual: boolean.">true</warning>
                         }""", "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testReferencedDeprecationWithSiblingAnyOf() {
    myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
    mySchemaFixture.configureInitially("""
                         {
                           "type": "object",
                           "properties": {
                             "old": {
                               "$ref": "#/definitions/old",
                               "anyOf": [
                                 {
                                   "minLength": 1
                                 },
                                 {
                                   "maxLength": 0
                                 }
                               ]
                             }
                           },
                           "definitions": {
                             "old": {
                               "deprecationMessage": "Old property",
                               "type": "string"
                             }
                           }
                         }""",
                       """
                         {
                           <weak_warning descr="Property 'old' is deprecated: Old property">"old"</weak_warning>: <warning descr="Incompatible types.
                          Required one of: string. Actual: boolean.">true</warning>
                         }""", "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testIfThenElseFlat() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/ifThenElseFlatSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      {
        "street_address": "24 Sussex Drive",
        "country": "Canada",
        "postal_code": "K1M 1M4"\s
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "street_address": "24 Sussex Drive",
        "country": "Canada",
        "postal_code": <warning descr="String violates the pattern: '[A-Z][0-9][A-Z] [0-9][A-Z][0-9]'">"1K1M1M4"</warning>\s
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "street_address": "24 Madison Cube Garden NYC",
        "country": "United States of America",
        "postal_code": "11222-1111-1111"
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "street_address": "24 Madison Cube Garden NYC",
        "country": "United States of America",
        "postal_code": <warning descr="String violates the pattern: '[0-9]{5}(-[0-9]{4})?'">"1-1111-1111"</warning>
      }""");
  }

  @Test
  public void testProhibitAdditionalPropsAlternateBranches() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/prohibitedAlternateBranchesSchema.json"));
    mySchemaFixture.doTest(schemaText, """
      {
        "subject": {
          "discriminator": "first",
          "first": false,
          <warning descr="Property 'second' is not allowed">"second"</warning>: false
        }
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "subject": {
          "discriminator": "second",
          <warning descr="Property 'first' is not allowed">"first"</warning>: false,
          "second": false
        }
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "subject": {
          "discriminator": "second",
          "second": false
        }
      }""");
    mySchemaFixture.doTest(schemaText, """
      {
        "subject": {
          "discriminator": "first",
          "first": false
        }
      }""");
  }

  @Test
  public void testPropertyNamesRef() {
    mySchemaFixture.doTest("""
                             {
                               "$schema": "http://json-schema.org/draft-07/schema#",
                               "definitions": {
                                 "Ref": {
                                   "enum": ["a", "b", "c"]
                                 }
                               },
                               "patternProperties": {
                                 ".*": {
                                   "propertyNames": {
                                     "$ref": "#/definitions/Ref"
                                   }
                                 }
                               }
                             }""", """
                             {
                               "Name": {
                                 <warning>"d"</warning>: "a"
                               }
                             }""");
  }

  @Test
  public void testCaseInsensitive() {
    mySchemaFixture.doTest("""
                             {
                               "$schema": "http://json-schema.org/draft-07/schema#",
                               "additionalProperties": {
                                 "x-intellij-case-insensitive": true,
                                 "enum": ["aa", "bb"]
                               }
                             }""", "{\"q\": \"aA\", \"r\": \"Bb\", \"s\": <warning>\"aB\"</warning>}");
  }

  @Test
  public void testFunctionSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/functionSchema.json"));
    mySchemaFixture.doTest(schemaText, "{\"bindings\": [\"queueTrigger\"]}");
  }

  @Test
  public void testLargeInteger() {
    mySchemaFixture.doTest("""
                             {
                               "properties": {
                                 "sampled": {
                                   "type": "integer",
                                   "minimum": 0
                                 }
                               }
                             }""", """
                             {
                               "sampled": 15123456789\s
                             }
                             """);
  }

  @Test
  public void testReducedTopLevelRangeHighlighting() {
    mySchemaFixture.doTest("{ \"required\": [\"test3\"]}",
                           "{ <warning descr=\"Missing required property 'test3'\">\"test1\": 123</warning>, \"test2\": 456}");
  }

  @Test
  public void testTopLevelRangeHighlighting() {
    mySchemaFixture.doTest("{ \"required\": [\"test3\"]}",
                           "<warning descr=\"Missing required property 'test3'\">{}</warning>");
  }

  // IJ validator silently ignores unresolvable $ref (broken branch dropped, no error on "name")
  @Test
  public void testBrokenRefAcceptsAnyValue() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "name": { "$ref": "#/definitions/nonExistent" },
          "age": { "type": "number" }
        }
      }""";
    // "name" uses broken $ref → IJ drops it → any value accepted (no error)
    // "age" still validated → type error reported
    mySchemaFixture.doTest(schema, "{\"name\": {\"garbage\": true}, \"age\": <warning descr=\"Incompatible types.\n Required: number. Actual: string.\">\"not a number\"</warning>}");
  }

  // IJ validator silently ignores unresolvable $ref inside allOf — allOf branch is dropped
  @Test
  public void testBrokenRefInAllOfAcceptsAnyValue() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "data": {
            "allOf": [
              { "$ref": "#/definitions/nonExistent" },
              { "type": "object" }
            ]
          },
          "count": { "type": "integer" }
        }
      }""";
    // "data" uses broken $ref in allOf — IJ drops broken branch → still validates remaining allOf
    // constraints (type: object passes for {}) → no error on "data"
    // "count" still validated → type error reported
    mySchemaFixture.doTest(schema, "{\"data\": {}, \"count\": <warning descr=\"Incompatible types.\n Required: integer. Actual: string.\">\"not a number\"</warning>}");
  }

  // IJ validator drops broken $ref branches in oneOf, affecting the error message
  @Test
  public void testOneOfWithBrokenRefBranch() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "value": {
            "oneOf": [
              { "type": "string" },
              { "$ref": "#/definitions/nonExistent" },
              { "type": "number" }
            ]
          }
        }
      }""";
    // "hello" matches the string branch → oneOf passes (broken branch dropped silently) → no error
    mySchemaFixture.doTest(schema, "{\"value\": \"hello\"}");
    // true matches no valid branch (string fails, broken branch dropped, number fails) →
    // IJ reports 2-type oneOf error (broken branch not counted)
    mySchemaFixture.doTest(schema, "{\"value\": <warning descr=\"Incompatible types.\n Required one of: number, string. Actual: boolean.\">true</warning>}");
  }

  @Test
  public void testPatternWithAlternationAndPrefixAnchor() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "version": {
            "type": "string",
            "pattern": "^v?\\\\d+(((\\\\.\\\\d+)?\\\\.\\\\d+)?\\\\.\\\\d+)?|^dev-"
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "{\"version\": \"1.0.0-dev\"}");
    mySchemaFixture.doTest(schema, "{\"version\": \"v2.0.4-p1\"}");
    mySchemaFixture.doTest(schema, "{\"version\": \"dev-main\"}");
    mySchemaFixture.doTest(schema, "{\"version\": <warning descr=\"String violates the pattern: '^v?\\d+(((\\.\\d+)?\\.\\d+)?\\.\\d+)?|^dev-'\">\"invalid-version\"</warning>}");
  }
}
