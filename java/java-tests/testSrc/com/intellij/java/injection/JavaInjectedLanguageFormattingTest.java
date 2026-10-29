package com.intellij.java.injection;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/java/java-tests/testData/injection/formatter")
public class JavaInjectedLanguageFormattingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testDoNotFormatInjectedComment() {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(projectFixture.get()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean before = settings.ALIGN_MULTILINE_BINARY_OPERATION;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    try {
      doInjectionTest(".java");
    }
    finally {
      settings.ALIGN_MULTILINE_BINARY_OPERATION = before;
    }
  }

  private void doInjectionTest(String extension) {
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeInsightTestFixture myFixture = codeInsightFixture.get();
      String prefix = testName;
      myFixture.configureByFile(prefix + extension);
      forceInjectionOnFile(myFixture.getFile());
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(element);
      PsiFile file = element.getContainingFile();
      FileViewProvider provider = file.getViewProvider();
      assertTrue(InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file), provider.toString());
      WriteCommandAction.runWriteCommandAction(null, () -> {
        CodeStyleManager.getInstance(projectFixture.get()).reformat(myFixture.getFile());
      });
      myFixture.checkResultByFile(prefix + "-after"+ extension);
    });
  }

  private static void forceInjectionOnFile(PsiFile file) {
    file.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        InjectedLanguageManager.getInstance(element.getProject()).enumerate(element, (_, _)-> {});
        super.visitElement(element);
      }
    });
  }
}
