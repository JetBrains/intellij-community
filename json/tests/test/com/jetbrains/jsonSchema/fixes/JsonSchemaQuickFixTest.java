// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fixes;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;

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

  public void testAddMissingProperty() throws Exception {
    doTest("""
             {
               "properties": {
                 "a": {
                   "default": "q"
                 }
               },
               "required": ["a", "b"]
             }""", "<warning>{<caret>\"c\": 5}</warning>", "Add missing properties 'a', 'b'", """
             {"c": 5,
               "a": "q",
               "b":
             }""");
  }

  public void testAddMissingNonStringProperties() throws Exception {
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

  public void testRemoveProhibitedProperty() throws Exception {
    doTest("""
             {
               "properties": {
                 "a": {},
                 "c": {}
               },
               "additionalProperties": false
             }""", "{\"a\": 5, <warning><caret>\"b\": 6</warning>, \"c\": 7}", "Remove prohibited property 'b'", "{\"a\": 5,\n" +
                                                                                                                 "  \"c\": 7}");
  }
}
