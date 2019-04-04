// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;

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
  private void doTest(@Language("JSON") String schema, @Language("JSON") String text, boolean shouldHaveInjection) throws Exception {
    final PsiFile file = configureInitially(schema, text, "json");
    PsiElement injectedElement = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, getEditor().getCaretModel().getOffset());
    assertSame(shouldHaveInjection, injectedElement != null);
  }

  public void testXml() throws Exception {
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

  public void testNoInjection() throws Exception {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"X\": {\n" +
           "    }\n" +
           "  }\n" +
           "}", "{\n" +
                "  \"X\": \"<a<caret>></a>\"\n" +
                "}", false);
  }
}
