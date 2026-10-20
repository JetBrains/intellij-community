// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fixes;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixture;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixtureKt.jsonSchemaHighlightingFixture;

@TestApplication
public class JsonSchemaQuickFixTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHighlightingTestFixture> schemaFixture =
    jsonSchemaHighlightingFixture(codeInsightFixture, "config.json", () -> new JsonSchemaComplianceInspection(), JsonLanguage.INSTANCE);

  private CodeInsightTestFixture myFixture;
  private JsonSchemaHighlightingTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
  }

  @Test
  public void testAddMissingProperty() {
    mySchemaFixture.doTest("""
                             {
                               "properties": {
                                 "a": {
                                   "default": "q"
                                 }
                               },
                               "required": ["a", "b"]
                             }""", "{<warning descr=\"Missing required properties 'a', 'b'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"c<caret>\": 5</warning>}",
                           "Add missing properties 'a', 'b'", """
                             {
                               "c": 5,
                               "a": "q",
                               "b":
                             }""");
  }

  @Test
  public void testAddMissingNonStringProperties() {
    mySchemaFixture.doTest("""
                             {
                               "required": ["x", "y"],
                               "properties": {
                                 "x": {
                                   "type": "boolean",
                                   "default": true
                                 },
                                 "y": {
                                   "type": "number",
                                   "default": 1
                                 }
                               }
                             }""", "<warning>{<caret>}</warning>", "Add missing properties 'x', 'y'", """
                             {
                               "x": true,
                               "y": 1
                             }""");
  }

  @Test
  public void testRemoveProhibitedProperty() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "a": {},
          "c": {}
        },
        "additionalProperties": false
      }""";
    String fixName = "Remove prohibited property 'b'";
    mySchemaFixture.doTest(schema, "{\"a\": 5, <warning><caret>\"b\"</warning>: 6, \"c\": 7}", fixName, "{\"a\": 5,\n  \"c\": 7}");
    mySchemaFixture.doTest(schema, "{\"a\": 5, \"c\": 7, <warning><caret>\"b\"</warning>: 6}", fixName, "{\"a\": 5, \"c\": 7}");
    mySchemaFixture.doTest(schema, "{<warning><caret>\"b\"</warning>: 6, \"a\": 5, \"c\": 7}", fixName, "{\n  \"a\": 5, \"c\": 7}");
    mySchemaFixture.doTest(schema, "{<warning><caret>\"b\"</warning>: 6}", fixName, "{}");
  }

  @Test
  public void testRemoveProhibitedPropertyInjection() {
    myFixture.setCaresAboutInjection(false);
    @Language("JSON") String schema = """
      {
        "properties": {
          "inner": {
            "properties": {"x": {}},
            "additionalProperties": false
          },
          "outer": {
            "x-intellij-language-injection": "JSON"
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
                             {"outer": "{\\"inner\\": \
                             {\\"x\\": 1, <warning descr="Property 'y' is not allowed"><caret>\\"</warning><warning descr="Property 'y' is not allowed">y</warning>\
                             <warning descr="Property 'y' is not allowed">\\"</warning>: 2}}"}""",
                           "Remove prohibited property 'y'", """
                             {"outer": "{\\"inner\\": {\\"x\\": 1}}"}""");
  }

  @Test
  public void testSuggestEnumValuesFix() {
    @Language("JSON") String schema = """
      {
        "required": ["x", "y"],
        "properties": {
          "x": {
            "enum": ["xxx", "yyy", "zzz"]
          },
          "y": {
            "enum": [1, 2, 3, 4, 5]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      {
       "x": <warning descr="Value should be one of: \\"xxx\\", \\"yyy\\", \\"zzz\\"">"<caret>oops"</warning>,
       "y": <warning descr="Value should be one of: 1, 2, 3, 4, 5">123</warning>
      }""", "Replace with allowed value", """
      {
       "x": "xxx",
       "y": 123
      }""");
    mySchemaFixture.doTest(schema, """
      {
       "x": <warning descr="Value should be one of: \\"xxx\\", \\"yyy\\", \\"zzz\\"">"oops"</warning>,
       "y": <warning descr="Value should be one of: 1, 2, 3, 4, 5"><caret>123</warning>
      }""", "Replace with allowed value", """
      {
       "x": "oops",
       "y": 1
      }""");
  }

  @Test
  public void testSuggestEnumValuesFixInjection() {
    myFixture.setCaresAboutInjection(false);
    @Language("JSON") String schema = """
      {
        "properties": {
          "inner": {
            "enum": [10, 20, 30]
          },
          "outer": {
            "x-intellij-language-injection": "JSON"
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      {"outer": "{\\"inner\\": <warning descr="Value should be one of: 10, 20, 30"><caret>123</warning>}"}""",
           "Replace with allowed value", """
             {"outer": "{\\"inner\\": 10}"}""");
  }

  @Test
  public void testAddMissingPropertyWithReformatting() {
    mySchemaFixture.doTest("""
                             {
                                 "properties": {
                                   "items": {
                                      "type": "array",
                                      "items": {
                                        "type": "object",
                                        "properties": {
                                          "id":   { "type": "string" },
                                          "name": { "type": "string" }
                                        },
                                        "required": ["id", "name"]
                                      }
                                   }
                                 }
                               }""", """
                             {
                                 "items": [
                                     <warning descr="Missing required property 'name'">{
                                       "id":     "1"<caret>
                                     }</warning>
                                 ]
                             }""", "Add missing property 'name'", """
                             {
                               "items": [
                                 {
                                   "id": "1",
                                   "name": ""
                                 }
                               ]
                             }""");
  }
}
