// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.json5.Json5FileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.InjectionTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.JsonSchemaTestProvider;
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.intellij.lang.regexp.RegExpLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/spellchecker")
public class JsonSpellcheckerTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<String> testNameFixture = testNameFixture(false);

  private CodeInsightTestFixture myFixture;
  private String myTestName;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    myTestName = testNameFixture.get();
  }

  private void doTest() {
    myFixture.enableInspections(GrazieSpellCheckingInspection.class);
    myFixture.configureByFile(myTestName + ".json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testEscapeAwareness() {
    doTest();
  }

  @Test
  public void testSimple() {
    doTest();
  }

  private static Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      JsonLanguage.INSTANCE);
  }

  @Test
  public void testWithSchema() {
    PsiFile[] files = myFixture.configureByFiles(myTestName + ".json", "Schema.json");
    JsonSchemaTestServiceImpl.setProvider(new JsonSchemaTestProvider(files[1].getVirtualFile(), getAvailabilityPredicate()));
    ServiceContainerUtil.replaceService(myFixture.getProject(), JsonSchemaService.class, new JsonSchemaTestServiceImpl(myFixture.getProject()), myFixture.getTestRootDisposable());
    Disposer.register(myFixture.getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        JsonSchemaTestServiceImpl.setProvider(null);
      }
    });
    myFixture.enableInspections(GrazieSpellCheckingInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  // WEB-31894 EA-117068
  @Test
  public void testAfterModificationOfStringLiteralWithEscaping() {
    myFixture.configureByFile(myTestName + ".json");
    myFixture.enableInspections(GrazieSpellCheckingInspection.class);
    myFixture.checkHighlighting();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.doHighlighting();
  }

  @Test
  public void testHashesQuotedSpelling() {
    myFixture.enableInspections(GrazieSpellCheckingInspection.class);
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

  @Test
  public void testInjectedFragments() {
    myFixture.enableInspections(GrazieSpellCheckingInspection.class);

    myFixture.configureByText(Json5FileType.INSTANCE, """
    {
      "success": /* language=RegExp */ "[i<caret>like]?"
    }
    """.stripIndent());

    ReadAction.runBlocking(() -> new InjectionTestFixture(myFixture)
      .assertInjectedLangAtCaret(RegExpLanguage.INSTANCE.getID()));

    myFixture.checkHighlighting(true, false, true);
  }
}
