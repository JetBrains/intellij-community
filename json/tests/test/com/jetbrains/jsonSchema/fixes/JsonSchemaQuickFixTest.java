// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.fixes;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;

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
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"a\": {\n" +
           "      \"default\": \"q\"\n" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"a\", \"b\"]\n" +
           "}", "<warning>{\"c\": 5}</warning>", "Add missing properties 'a', 'b'", "{\"c\": 5,\n" +
                                                                                    "  \"a\": \"q\",\n" +
                                                                                    "  \"b\":\n" +
                                                                                    "}");
  }

  // todo fix working with live template in test; test-only problem
  /*public void testAddMissingStringProperty() throws Exception {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"a\": {\n" +
           "      \"type\": \"string\"" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"a\"]\n" +
           "}", "<warning>{\"c\": 5}</warning>", "Add missing property 'a'", "{\"c\": 5,\n" +
                                                                             "  \"a\": \"<caret>\"" +
                                                                             "\n}");
  }*/

  public void testRemoveProhibitedProperty() throws Exception {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"a\": {},\n" +
           "    \"c\": {}\n" +
           "  },\n" +
           "  \"additionalProperties\": false\n" +
           "}", "{\"a\": 5, <warning>\"b\": 6</warning>, \"c\": 7}", "Remove prohibited property 'b'", "{\"a\": 5,\n" +
                                                                                                       "  \"c\": 7}");
  }
}
