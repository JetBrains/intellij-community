// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaDeprecationInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class JsonSchemaHighlightingTest extends JsonSchemaHighlightingTestBase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/jsonSchema/highlighting";
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

  public void testNumberMultipleWrong() {
    doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}",
           "{ \"prop\": <warning descr=\"Is not multiple of 2\">3</warning>}");
  }

  public void testNumberMultipleCorrect() {
    doTest("{ \"properties\": { \"prop\": {\"type\": \"number\", \"multipleOf\": 2}}}", "{ \"prop\": 4}");
  }

  public void testNumberMinMax() {
    doTest("""
             { "properties": { "prop": {
               "type": "number",
               "minimum": 0,
               "maximum": 100,
               "exclusiveMaximum": true
             }}}""", "{ \"prop\": 14}");
  }

  public void testEnum() {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"enum\": [1,2,3,\"18\"]}}}";
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: 1, 2, 3, \\\"18\\\"\">18</warning>}");
    doTest(schema, "{\"prop\": 2}");
    doTest(schema, "{\"prop\": \"18\"}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: 1, 2, 3, \\\"18\\\"\">\"2\"</warning>}");
  }

  public void testSimpleString() {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"minLength\": 2, \"maxLength\": 3}}}";
    doTest(schema, "{\"prop\": <warning descr=\"String is shorter than 2\">\"s\"</warning>}");
    doTest(schema, "{\"prop\": \"sh\"}");
    doTest(schema, "{\"prop\": \"sho\"}");
    doTest(schema, "{\"prop\": <warning descr=\"String is longer than 3\">\"shor\"</warning>}");
  }

  public void testArray() {
    @Language("JSON") final String schema = schema("""
                                                     {
                                                       "type": "array",
                                                       "items": {
                                                         "type": "number", "minimum": 18  }
                                                     }""");
    doTest(schema, "{\"prop\": [101, 102]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Less than the minimum 18\">16</warning>]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Incompatible types.\n Required: number. Actual: string.\">\"test\"</warning>]}");
  }

  public void testTopLevelArray() {
    @Language("JSON") final String schema = """
      {
        "type": "array",
        "items": {
          "type": "number", "minimum": 18  }
      }""";
    doTest(schema, "[101, 102]");
  }

  public void testTopLevelObjectArray() {
    @Language("JSON") final String schema = """
      {
        "type": "array",
        "items": {
          "type": "object", "properties": {"a": {"type": "number"}}  }
      }""";
    doTest(schema, "[{\"a\": <warning descr=\"Incompatible types.\n Required: number. Actual: boolean.\">true</warning>}]");
    doTest(schema, "[{\"a\": 18}]");
  }

  public void testArrayTuples1() {
    @Language("JSON") final String schema = schema("""
                                                     {
                                                       "type": "array",
                                                       "items": [{
                                                         "type": "number", "minimum": 18  }, {"type" : "string"}]
                                                     }""");
    doTest(schema, "{\"prop\": [101, <warning descr=\"Incompatible types.\n Required: string. Actual: integer.\">102</warning>]}");
    doTest(schema, "{\"prop\": [101, \"102\"]}");
    doTest(schema, "{\"prop\": [101, \"102\", \"additional\"]}");
  }

  public void testArrayTuples2() {
    @Language("JSON") final String schema2 = schema("""
                                                      {
                                                        "type": "array",
                                                        "items": [{
                                                          "type": "number", "minimum": 18  }, {"type" : "string"}],
                                                      "additionalItems": false}""");
    doTest(schema2, "{\"prop\": [101, \"102\", <warning descr=\"Additional items are not allowed\">\"additional\"</warning>]}");
  }

  public void testArrayLength() {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    doTest(schema, "{\"prop\": <warning descr=\"Array is shorter than 2\">[]</warning>}");
    doTest(schema, "{\"prop\": [1,2]}");
    doTest(schema, "{\"prop\": <warning descr=\"Array is longer than 3\">[1,2,3,4]</warning>}");
  }

  public void testArrayUnique() {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    doTest(schema, "{\"prop\": [1,2]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Item is not unique\">1</warning>,2, \"test\", <warning descr=\"Item is not unique\">1</warning>]}");
  }

  public void testMetadataIsOk() {
    @Language("JSON") final String schema = """
      {
        "title" : "Match anything",
        "description" : "This is a schema that matches anything.",
        "default" : "Default value"
      }""";
    doTest(schema, "{\"anything\": 1}");
  }

  public void testRequiredField() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    doTest(schema, "{\"a\": 11}");
    doTest(schema, "{\"a\": 1, \"b\": true}");
    doTest(schema, "{<warning descr=\"Missing required property 'a'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"b\": \"alarm\"</warning>}");
  }

  public void testInnerRequired() {
    @Language("JSON") final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    doTest(schema, "{\"prop\": {\"a\": 11}}");
    doTest(schema, "{\"prop\": {\"a\": 1, \"b\": true}}");
    doTest(schema, "{\"prop\": <warning descr=\"Missing required property 'a'\">{\"b\": \"alarm\"}</warning>}");
  }

  public void testUseDefinition() {
    @Language("JSON") final String schema = "{\"definitions\": {\"address\": {\"type\": \"object\", \"properties\": {\"street\": {\"type\": \"string\"}," +
                                            "\"house\": {\"type\": \"integer\"}}}}," +
                                            "\"type\": \"object\", \"properties\": {" +
                                            "\"home\": {\"$ref\": \"#/definitions/address\"}, " +
                                            "\"office\": {\"$ref\": \"#/definitions/address\"}" +
                                            "}}";
    doTest(schema, "{\"home\": {\"street\": \"Broadway\", \"house\": 11}}");
    doTest(schema, """
      {"home": {"street": "Broadway", "house": <warning descr="Incompatible types.
       Required: integer. Actual: string.">"unknown"</warning>},"office": {"street": <warning descr="Incompatible types.
       Required: string. Actual: integer.">5</warning>}}""");
  }

  public void testAdditionalPropertiesAllowed() {
    @Language("JSON") final String schema = schema("{}");
    doTest(schema, "{\"prop\": {}, \"someStuff\": 20}");
  }

  public void testAdditionalPropertiesDisabled() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    doTest(schema, "{\"prop\": {}, <warning descr=\"Property 'someStuff' is not allowed\">\"someStuff\"</warning>: 20}");
  }

  public void testAdditionalPropertiesSchema() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                                            "\"additionalProperties\": {\"type\": \"string\"}}";
    doTest(schema, "{\"a\" : 18, \"b\": \"wall\", \"c\": <warning descr=\"Incompatible types.\n Required: string. Actual: integer.\">11</warning>}");
  }

  public void testMinMaxProperties() {
    @Language("JSON") final String schema = "{\"type\": \"object\", \"minProperties\": 1, \"maxProperties\": 2}";
    doTest(schema, "<warning descr=\"Number of properties is less than 1\">{}</warning>");
    doTest(schema, "{\"a\": 1}");
    doTest(schema, "{<warning descr=\"Number of properties is greater than 2\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"a\": 1</warning>, \"b\": 22, \"c\": 33}");
  }

  public void testOneOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"abc\"}");
    doTest(schema, "{\"prop\": true}");
    doTest(schema, "{\"prop\": <warning descr=\"Incompatible types.\n Required one of: boolean, string. Actual: integer.\">11</warning>}");
  }

  public void testOneOfForTwoMatches() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"b\"}");
    doTest(schema, "{\"prop\": \"c\"}");
    doTest(schema, "{\"prop\": <warning descr=\"Validates to more than one variant\">\"a\"</warning>}");
  }

  public void testOneOfSelectError() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {"type": "string",
                               "enum": [
                                 "off", "warn", "error"
                               ]}""");
    subSchemas.add("{\"type\": \"integer\"}");
    @Language("JSON") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"off\"}");
    doTest(schema, "{\"prop\": 12}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: \\\"off\\\", \\\"warn\\\", \\\"error\\\"\">\"wrong\"</warning>}");
  }

  public void testAnyOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("JSON") final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": \"b\"}");
    doTest(schema, "{\"prop\": \"c\"}");
    doTest(schema, "{\"prop\": \"a\"}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: \\\"a\\\", \\\"b\\\", \\\"c\\\"\">\"d\"</warning>}");
  }

  public void testAllOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"integer\", \"multipleOf\": 2}");
    subSchemas.add("{\"enum\": [1,2,3]}");
    @Language("JSON") final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTest(schema, "{\"prop\": <warning descr=\"Is not multiple of 2\">1</warning>}");
    doTest(schema, "{\"prop\": <warning descr=\"Value should be one of: 1, 2, 3\">4</warning>}");
    doTest(schema, "{\"prop\": 2}");
  }

  public void testObjectInArray() {
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":{}, \"innerValue\":{}" +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTest(schema, "{\"prop\": [{\"innerType\":{}, <warning descr=\"Property 'alien' is not allowed\">\"alien\"</warning>:{}}]}");
  }

  public void testObjectDeeperInArray() {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    @Language("JSON") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":" + innerTypeSchema +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTest(schema,
           "{\"prop\": [{\"innerType\":{\"only\": true, <warning descr=\"Property 'hidden' is not allowed\">\"hidden\"</warning>: false}}]}");
  }

  public void testInnerObjectPropValueInArray() {
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    doTest(schema, "{\"prop\": [1,3]}");
    doTest(schema, "{\"prop\": [<warning descr=\"Value should be one of: 1, 2, 3\">\"out\"</warning>]}");
  }

  public void testAllOfProperties() {
    @Language("JSON") final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                                                                                " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
    doTest(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: 33, 44\">null</warning>}");
    doTest(schema, "{\"first\": {}, \"second\": 44, <warning descr=\"Property 'other' is not allowed\">\"other\"</warning>: 15}");
    doTest(schema, "{\"first\": {}, \"second\": <warning descr=\"Value should be one of: 33, 44\">12</warning>}");
  }

  public void testWithWaySelection() {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    @Language("JSON") final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    //doTest(schema, "{\"prop\": [{\"kilo\": 20}]}");
    //doTest(schema, "{\"prop\": 5}");
    doTest(schema, "{\"prop\": [{<warning descr=\"Property 'foxtrot' is not allowed\">\"foxtrot\"</warning>: 15, \"kilo\": 20}]}");
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
    doTest(schema, """
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
    doTest(schema, """
      {
        "p1": <warning descr="Incompatible types.
       Required: string. Actual: integer.">1</warning>,
        "p2": "3",
        "a2": "auto!",
        "a1": <warning descr="Value should be one of: \\"auto!\\"">"moto!"</warning>
      }""");
  }

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
    doTest(schema, correctText);
    doTest(schema, wrongText);
  }

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
    doTest(schema, correctText);
    doTest(schema, wrongText);
  }

  public void testRootObjectRedefinedAdditionalPropertiesForbidden() {
    doTest(rootObjectRedefinedSchema(), "{<warning descr=\"Property 'a' is not allowed\">\"a\"</warning>: true," +
                                        "\"r1\": \"allowed!\"}");
  }

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
    doTest(schema, """
      {
        "size": {
          "a": <warning descr="Incompatible types.
       Required: boolean. Actual: integer.">1</warning>, "b":3, "c": 4, "a": <warning descr="Incompatible types.
       Required: boolean. Actual: integer.">5</warning>
        }
      }""");
    doTest(schema, """
      {
        "size": <warning descr="Number of properties is greater than 3">{
          "a": true, "b":3, "c": 4, "a": false
        }</warning>
      }""");
  }

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
    doTest(schema, "{\"array\": [<warning descr=\"Item is not unique\">1</warning>," +
                   "<warning descr=\"Item is not unique\">1</warning>," +
                   "<warning descr=\"Item is not unique\">1</warning>," +
                   "<warning descr=\"Item is not unique\">2</warning>," +
                   "<warning descr=\"Item is not unique\">2</warning>," +
                   "5," +
                   "<warning descr=\"Item is not unique\">3</warning>," +
                   "<warning descr=\"Item is not unique\">3</warning>]}");
  }

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
    doTest(schema, text);
  }

  public void testNotSchema() {
    @Language("JSON") final String schema = """
      {"properties": {
          "not_type": { "not": { "type": "string" } }
        }}""";
    doTest(schema, "{\"not_type\": <warning descr=\"Validates against 'not' schema\">\"wrong\"</warning>}");
  }

  public void testNotSchemaCombinedWithNormal() {
    @Language("JSON") final String schema = """
      {"properties": {
          "not_type": {
            "pattern": "^[a-z]*[0-5]*$",
            "not": { "pattern": "^[a-z]{1}[0-5]$" }
          }
        }}""";
    doTest(schema, "{\"not_type\": \"va4\"}");
    doTest(schema, "{\"not_type\": <warning descr=\"Validates against 'not' schema\">\"a4\"</warning>}");
    doTest(schema, "{\"not_type\": <warning descr=\"String violates the pattern: '^[a-z]*[0-5]*$'\">\"4a4\"</warning>}");
  }

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
    doTest(schema, "{\"withFormat\": \"localhost\"}");
  }

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
    doTest(schema, "{\"withFormat\": \"localhost\"}");
  }

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
    doTest(schema, "[1, 2]");
    doTest(schema, "[1, <warning>\"foo\"</warning>]");
  }

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
    doTest(schema, "[2, 3 ,4]");
    doTest(schema, "[2, <warning>\"3\"</warning>]");
  }

  public void testSelfArrayReferenceDoesNotThrowSOE() {
    @Language("JSON") final String schema = """
      {
        "items": [
          {
            "$ref": "#/items/0"
          }
        ]
      }""";
    doTest(schema, "[]");
  }

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
    doTest(schema, "[true, true]");
    doTest(schema, "[true, true, 1, 2, 3]");
    doTest(schema, "[true, true, 1, <warning>\"2\"</warning>]");
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

  public void testExclusiveMinMaxV6_1() {
    @Language("JSON") String exclusiveMinSchema = "{\"properties\": {\"prop\": {\"exclusiveMinimum\": 3}}}";
    doTest(exclusiveMinSchema, "{\"prop\": <warning>2</warning>}");
    doTest(exclusiveMinSchema, "{\"prop\": <warning>3</warning>}");
    doTest(exclusiveMinSchema, "{\"prop\": 4}");
  }

  public void testExclusiveMinMaxV6_2() {
    @Language("JSON") String exclusiveMaxSchema = "{\"properties\": {\"prop\": {\"exclusiveMaximum\": 3}}}";
    doTest(exclusiveMaxSchema, "{\"prop\": 2}");
    doTest(exclusiveMaxSchema, "{\"prop\": <warning>3</warning>}");
    doTest(exclusiveMaxSchema, "{\"prop\": <warning>4</warning>}");
  }

  public void testPropertyNamesV6_1() {
    doTest("{\"propertyNames\": {\"minLength\": 7}}", "{<warning>\"prop\"</warning>: 2}");
  }

  public void testPropertyNamesV6_2() {
    doTest("{\"properties\": {\"prop\": {\"propertyNames\": {\"minLength\": 7}}}}", "{\"prop\": {<warning>\"qq\"</warning>: 7}}");
  }

  public void testContainsV6() {
    @Language("JSON") String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"contains\": {\"type\": \"number\"}}}}";
    doTest(schema, "{\"prop\": <warning>[{}, \"a\", true]</warning>}");
    doTest(schema, "{\"prop\": [{}, \"a\", 1, true]}");
  }

  public void testConstV6() {
    @Language("JSON") String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"const\": \"foo\"}}}";
    doTest(schema, "{\"prop\": <warning>\"a\"</warning>}");
    doTest(schema, "{\"prop\": <warning>5</warning>}");
    doTest(schema, "{\"prop\": \"foo\"}");
  }

  public void testIfThenElseV7() {
    @Language("JSON") String schema = """
      {
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
    doTest(schema, "<warning>{}</warning>");
    doTest(schema, "{\"c\": <warning descr=\"Incompatible types.\n Required: boolean. Actual: integer.\">5</warning>}");
    doTest(schema, "{\"c\": true}");
    doTest(schema, "{<warning descr=\"Missing required property 'c'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"a\": 5</warning>, \"b\": 5}");
    doTest(schema, "{\"a\": 5, \"c\": <warning>5</warning>}");
    doTest(schema, "{\"a\": 5, \"c\": true}");
    doTest(schema, "{<warning descr=\"Missing required property 'b'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"a\": \"a\"</warning>, \"c\": true}");
    doTest(schema, "{\"a\": \"a\", \"b\": <warning>true</warning>}");
    doTest(schema, "{\"a\": \"a\", \"b\": 5}");
  }

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

    doTest(schema, "{\"type\": \"good\"}");
    doTest(schema, "{\"type\": \"ok\"}");
    doTest(schema, "{\"type\": <warning>\"doog\"</warning>}");
    doTest(schema, "{\"type\": <warning>\"ko\"</warning>}");
  }

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

    doTest(schema, "[1, <warning>2</warning>]");
    doTest(schema, "[<warning>\"1\"</warning>, <warning>2</warning>]");
    doTest(schema, "[<warning>\"1\"</warning>, \"2\"]");
    doTest(schema, "[1, \"2\"]");
  }

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

    doTest(schema, """
      {
        "foo": {
          "provider": "npm"
        }
      }""");

    doTest(schema, """
      {
        "foo": {
          "provider": "script"
        }
      }""");

    doTest(schema, """
      {
        "foo": {
          "provider": <warning>"etwasanderes"</warning>
        }
      }""");
  }

  public void testOneOfBestChoiceSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/oneOfBestChoiceSchema.json"));
    doTest(schemaText, """
      {
        "results": [
          <warning descr="Missing required properties 'dateOfBirth', 'name'">{
            "type": "person"
          }</warning>
        ]
      }""");
  }

  public void testAnyOfBestChoiceSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/anyOfBestChoiceSchema.json"));
    doTest(schemaText, """
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

  public void testComplexOneOfSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/complexOneOfSchema.json"));
    doTest(schemaText, """
      {
          "indentation": "tab"
        }""");
    doTest(schemaText, """
      {
          "indentation": <warning>"ttab"</warning>
        }""");
  }

  public void testEnumCasing() {
    @Language("JSON") String schema = """
      {
        "type": "object",

        "properties": {
          "name": { "type": "string", "enum": ["aa", "bb"] }
        }
      }""";
    doTest(schema, """
      {
        "name": "aa"
      }""");
    doTest(schema, """
      {
        "name": <warning>"aA"</warning>
      }""");
  }

  public void testEnumArrayValue() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "foo": {
            "enum": [ [{"x": 5}, [true], "q"] ]
          }
        }
      }""";
    doTest(schema, "{\"foo\": <warning>5</warning>}");
    doTest(schema, "{\"foo\": <warning>[ ]</warning>}");
    doTest(schema, "{\"foo\": <warning>[{\"x\": 5}]</warning>}");
    doTest(schema, "{\"foo\": <warning>[{\"x\": 5}, true]</warning>}");
    doTest(schema, "{\"foo\": <warning>[{\"x\": 5}, [true]]</warning>}");
    doTest(schema, "{\"foo\": [  { \"x\"   :   5 }  ,  [ true ]  , \"q\"  ]}");
  }

  public void testEnumObjectValue() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "foo": {
            "enum": [ {"x": 5} ]
          }
        }
      }""";
    doTest(schema, "{\"foo\": <warning>{}</warning>}");
    doTest(schema, "{\"foo\": <warning>{\"x\": 4}</warning>}");
    doTest(schema, "{\"foo\": <warning>{\"x\": true}</warning>}");
    doTest(schema, "{\"foo\": { \r  \"x\"  : \t  5 \n  }}");
  }

  public void testIntersectingHighlightingRanges() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/avroSchema.json"));
    doTest(schemaText, """
      {
        <warning descr="Missing required property 'items'" textAttributesKey="WARNING_ATTRIBUTES">"type": "array"</warning>
      }""");
    doTest(schemaText, """
      {
        "type": <warning descr="Value should be one of: \\"array\\", \\"enum\\", \\"fixed\\", \\"map\\", \\"record\\"">"array2"</warning>
      }""");
  }

  public void testMissingMultipleAltPropertySets() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/avroSchema.json"));
    doTest(schemaText, """
      <warning descr="One of the following property sets is required: properties 'type' = record, 'fields', 'name', properties 'type' = enum, 'name', 'symbols', properties 'type' = array, 'items', properties 'type' = map, 'values', or properties 'type' = fixed, 'name', 'size'">{
       \s
      }</warning>""");
  }

  public void testValidateEnumVsPattern() {
    doTest("""
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

  public void testJsonPointerEscapes() {
    doTest("""
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

  public void testOneOfMultipleBranches() {
    doTest("""
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

  public void testReferenceById() {
    doTest("""
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

  public void testComplicatedConditions() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/complicatedConditions_schema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/complicatedConditions.json"));
    doTest(schemaText, inputText);
  }

  public void testExoticProps() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/exoticPropsSchema.json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/exoticProps.json"));
    doTest(schemaText, inputText);
  }

  public void testLargeInt() {
    // currently we limit it by Java Long range, should be sufficient as per RFC 7159
    doTest("""
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

  public void testMultipleIfThenElse() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/multipleIfThenElseSchema.json"));
    doTest(schemaText, """
      {
        "street_address": "1600 Pennsylvania Avenue NW",
        "country": "United States of America",
        "postal_code": "20500"
      }""");
    doTest(schemaText, """
      {
        "street_address": "1600 Pennsylvania Avenue NW",
        "country": "Netherlands",
        "postal_code": <warning descr="String violates the pattern: '[0-9]{4} [A-Z]{2}'">"20500"</warning>
      }""");
  }

  public void testDeprecation() throws IOException {
    myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/deprecation.json"));
    configureInitially(schemaText,
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

  public void testIfThenElseFlat() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/ifThenElseFlatSchema.json"));
    doTest(schemaText, """
      {
        "street_address": "24 Sussex Drive",
        "country": "Canada",
        "postal_code": "K1M 1M4"\s
      }""");
    doTest(schemaText, """
      {
        "street_address": "24 Sussex Drive",
        "country": "Canada",
        "postal_code": <warning descr="String violates the pattern: '[A-Z][0-9][A-Z] [0-9][A-Z][0-9]'">"1K1M1M4"</warning>\s
      }""");
    doTest(schemaText, """
      {
        "street_address": "24 Madison Cube Garden NYC",
        "country": "United States of America",
        "postal_code": "11222-1111-1111"
      }""");
    doTest(schemaText, """
      {
        "street_address": "24 Madison Cube Garden NYC",
        "country": "United States of America",
        "postal_code": <warning descr="String violates the pattern: '[0-9]{5}(-[0-9]{4})?'">"1-1111-1111"</warning>
      }""");
  }

  public void testProhibitAdditionalPropsAlternateBranches() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/prohibitedAlternateBranchesSchema.json"));
    doTest(schemaText, """
      {
        "subject": {
          "discriminator": "first",
          "first": false,
          <warning descr="Property 'second' is not allowed">"second"</warning>: false
        }
      }""");
    doTest(schemaText, """
      {
        "subject": {
          "discriminator": "second",
          <warning descr="Property 'first' is not allowed">"first"</warning>: false,
          "second": false
        }
      }""");
    doTest(schemaText, """
      {
        "subject": {
          "discriminator": "second",
          "second": false
        }
      }""");
    doTest(schemaText, """
      {
        "subject": {
          "discriminator": "first",
          "first": false
        }
      }""");
  }

  public void testPropertyNamesRef() {
    doTest("""
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

  public void testCaseInsensitive() {
    doTest("""
             {
               "$schema": "http://json-schema.org/draft-07/schema#",
               "additionalProperties": {
                 "x-intellij-case-insensitive": true,
                 "enum": ["aa", "bb"]
               }
             }""", "{\"q\": \"aA\", \"r\": \"Bb\", \"s\": <warning>\"aB\"</warning>}");
  }

  public void testFunctionSchema() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/functionSchema.json"));
    doTest(schemaText, "{\"bindings\": [\"queueTrigger\"]}");
  }

  public void testLargeInteger() {
    doTest("""
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

  public void testReducedTopLevelRangeHighlighting() {
    doTest("{ \"required\": [\"test3\"]}",
           "{ <warning descr=\"Missing required property 'test3'\">\"test1\": 123</warning>, \"test2\": 456}");
  }

  public void testTopLevelRangeHighlighting() {
    doTest("{ \"required\": [\"test3\"]}",
           "<warning descr=\"Missing required property 'test3'\">{}</warning>");
  }
}
