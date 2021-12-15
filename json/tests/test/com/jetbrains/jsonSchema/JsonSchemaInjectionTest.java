// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;

import java.util.function.Predicate;

public class JsonSchemaInjectionTest extends JsonSchemaHighlightingTestBase {

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

  @SuppressWarnings("SameParameterValue")
  private void doTest(@Language("JSON") String schema, @Language("JSON") String text, boolean shouldHaveInjection) {
    final PsiFile file = configureInitially(schema, text, "json");
    checkInjection(shouldHaveInjection, file, JsonFile.class);
  }

  public static void checkInjection(boolean shouldHaveInjection, PsiFile file, Class<? extends PsiFile> ownLanguageFileClass) {
    assertNotNull(file);
    if (shouldHaveInjection) {
      assertTrue(file.getClass().getSimpleName().startsWith("XmlFile"));
    }
    else {
      assertInstanceOf(file, ownLanguageFileClass);
    }
  }

  public void testXml() {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"X\": {\n" +
           "      \"x-intellij-language-injection\": \"XML\"\n" +
           "    }\n" +
           "  }\n" +
           "}", "{\n" +
                "  \"X\": \"<a<caret>></a>\"\n" +
                "}", true);
  }

  public void testNoInjection() {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"X\": {\n" +
           "    }\n" +
           "  }\n" +
           "}", "{\n" +
                "  \"X\": \"<a<caret>></a>\"\n" +
                "}", false);
  }

  public void testOneOfSchema() {
    @Language("JSON") String schema = "{\n" +
                                      "  \"additionalProperties\": {\n" +
                                      "    \"oneOf\": [\n" +
                                      "      {\n" +
                                      "        \"type\": \"string\",\n" +
                                      "        \"x-intellij-language-injection\": \"XML\"\n" +
                                      "      },\n" +
                                      "      {\n" +
                                      "        \"type\": \"array\",\n" +
                                      "        \"items\": {\n" +
                                      "          \"type\": \"string\",\n" +
                                      "          \"x-intellij-language-injection\": \"XML\"\n" +
                                      "        }\n" +
                                      "      }\n" +
                                      "    ]\n" +
                                      "  }\n" +
                                      "}";
    doTest(schema, "{\n" +
                "  \"foo\": \"<caret><a/>\",\n" +
                "  \"bar\": [\"<a/>\"]\n" +
                "}", true);
    doTest(schema, "{\n" +
                "  \"foo\": \"<a/>\",\n" +
                "  \"bar\": [\"<caret><a/>\"]\n" +
                "}", true);
  }

  public void testPrefixSuffix() {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"x\": {\n" +
           "      \"x-intellij-language-injection\": {\n" +
           "        \"language\": \"XML\",\n" +
           "        \"prefix\": \"<\",\n" +
           "        \"suffix\": \">\"\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}", "{\"x\": \"x><caret></\"}", true);
  }
}
