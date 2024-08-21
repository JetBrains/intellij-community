// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fixes;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;

import java.util.function.Predicate;

public class JsonSchemaQuickFixTest extends JsonSchemaQuickFixTestBase {
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

  public void testAddMissingProperty() {
    doTest("""
             {
               "properties": {
                 "a": {
                   "default": "q"
                 }
               },
               "required": ["a", "b"]
             }""", "{<warning descr=\"Missing required properties 'a', 'b'\" textAttributesKey=\"WARNING_ATTRIBUTES\">\"c<caret>\": 5</warning>}",
           "Add missing properties 'a', 'b'", """
             {"c": 5,
               "a": "q",
               "b":
             }""");
  }

  public void testAddMissingNonStringProperties() {
    doTest("""
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
    doTest(schema, "{\"a\": 5, <warning><caret>\"b\"</warning>: 6, \"c\": 7}", fixName, "{\"a\": 5,\n  \"c\": 7}");
    doTest(schema, "{\"a\": 5, \"c\": 7, <warning><caret>\"b\"</warning>: 6}", fixName, "{\"a\": 5, \"c\": 7}");
    doTest(schema, "{<warning><caret>\"b\"</warning>: 6, \"a\": 5, \"c\": 7}", fixName, "{\n  \"a\": 5, \"c\": 7}");
    doTest(schema, "{<warning><caret>\"b\"</warning>: 6}", fixName, "{}");
  }

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
    doTest(schema, """
             {"outer": "{\\"inner\\": \
             {\\"x\\": 1, <warning descr="Property 'y' is not allowed"><caret>\\"</warning><warning descr="Property 'y' is not allowed">y</warning>\
             <warning descr="Property 'y' is not allowed">\\"</warning>: 2}}"}""",
           "Remove prohibited property 'y'", """
             {"outer": "{\\"inner\\": {\\"x\\": 1}}"}""");
  }

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
    doTest(schema, """
      {
       "x": <warning descr="Value should be one of: \\"xxx\\", \\"yyy\\", \\"zzz\\"">"<caret>oops"</warning>,
       "y": <warning descr="Value should be one of: 1, 2, 3, 4, 5">123</warning>
      }""", "Replace with allowed value", """
      {
       "x": "xxx",
       "y": 123
      }""");
    doTest(schema, """
      {
       "x": <warning descr="Value should be one of: \\"xxx\\", \\"yyy\\", \\"zzz\\"">"oops"</warning>,
       "y": <warning descr="Value should be one of: 1, 2, 3, 4, 5"><caret>123</warning>
      }""", "Replace with allowed value", """
      {
       "x": "oops",
       "y": 1
      }""");
  }

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
    doTest(schema, """
      {"outer": "{\\"inner\\": <warning descr="Value should be one of: 10, 20, 30"><caret>123</warning>}"}""",
           "Replace with allowed value", """
             {"outer": "{\\"inner\\": 10}"}""");
  }
}
