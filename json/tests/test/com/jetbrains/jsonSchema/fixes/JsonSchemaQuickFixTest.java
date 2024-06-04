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
    doTest(schema, "{\"a\": 5, <warning><caret>\"b\": 6</warning>, \"c\": 7}", fixName, "{\"a\": 5,\n  \"c\": 7}");
    doTest(schema, "{\"a\": 5, \"c\": 7, <warning><caret>\"b\": 6</warning>}", fixName, "{\"a\": 5, \"c\": 7}");
    doTest(schema, "{<warning><caret>\"b\": 6</warning>, \"a\": 5, \"c\": 7}", fixName, "{\n  \"a\": 5, \"c\": 7}");
    doTest(schema, "{<warning><caret>\"b\": 6</warning>}", fixName, "{}");
  }
}
