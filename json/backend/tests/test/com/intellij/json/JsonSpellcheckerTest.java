// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.json5.Json5FileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.InjectionTestFixture;
import com.jetbrains.jsonSchema.JsonSchemaTestProvider;
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.intellij.lang.regexp.RegExpLanguage;

import java.util.function.Predicate;

/**
 * @author Mikhail Golubev
 */
public class JsonSpellcheckerTest extends JsonTestCase {

  private void doTest() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile(getTestName(false) + ".json");
    myFixture.checkHighlighting(true, false, true);
  }

  public void testEscapeAwareness() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      JsonLanguage.INSTANCE);
  }

  public void testWithSchema() {
    PsiFile[] files = myFixture.configureByFiles(getTestName(false) + ".json", "Schema.json");
    JsonSchemaTestServiceImpl.setProvider(new JsonSchemaTestProvider(files[1].getVirtualFile(), getAvailabilityPredicate()));
    ServiceContainerUtil.replaceService(getProject(), JsonSchemaService.class, new JsonSchemaTestServiceImpl(getProject()), getTestRootDisposable());
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        JsonSchemaTestServiceImpl.setProvider(null);
      }
    });
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  // WEB-31894 EA-117068
  public void testAfterModificationOfStringLiteralWithEscaping() {
    myFixture.configureByFile(getTestName(false) + ".json");
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.checkHighlighting();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.doHighlighting();
  }

  public void testHashesQuotedSpelling() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByText("hashes.json", """
    {
      "typo": "<TYPO>hereistheerror</TYPO>",
      "uuid": "f19c4bd2-4c11-4725-a613-06aaead4325e",
      "md5": "79054025255fb1a26e4bc422adfebeed",
      "sha1": "c3499c2729730aaff07efb8676a92dcb6f8a3f8f",
      "sha256": "50d858e0985ecc7f60418aaf0cc5ab587f42c2570a884095a9e8ccacd0f6545c",
      "jwt": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.dyt0CoTl4WoVjAHI9Q_CwSKhl6d_9rhM3NrXuJttkao"
    }
    """.stripIndent());

    myFixture.checkHighlighting(true, false, true);
  }

  public void testInjectedFragments() {
    myFixture.enableInspections(SpellCheckingInspection.class);

    myFixture.configureByText(Json5FileType.INSTANCE, """
    {
      "success": /* language=RegExp */ "[i<caret>like]?"
    }
    """.stripIndent());

    new InjectionTestFixture(myFixture)
      .assertInjectedLangAtCaret(RegExpLanguage.INSTANCE.getID());

    myFixture.checkHighlighting(true, false, true);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/spellchecker";
  }
}
