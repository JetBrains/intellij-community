// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.indentOptionsProvider;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.actions.IndentSelectionAction;
import com.intellij.openapi.editor.actions.UnindentSelectionAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestApplication
public class FileIndentProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  private final FileIndentOptionsProvider TEST_FILE_INDENT_OPTIONS_PROVIDER = new TestIndentOptionsProvider();
  private CommonCodeStyleSettings.IndentOptions myTestIndentOptions;
  private boolean myUseOnFullReformat;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/indentProvider");
    FileIndentOptionsProvider.EP_NAME.getPoint().registerExtension(TEST_FILE_INDENT_OPTIONS_PROVIDER, myFixture.getTestRootDisposable());
    myTestIndentOptions = new CommonCodeStyleSettings.IndentOptions();
  }

  private void doTestTyping(char c) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    myFixture.type(c);
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  private void doTestAction(@NotNull AnAction action) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    assertTrue(EdtTestUtil.runInEdtAndGet(() -> myFixture.testAction(action)).isEnabled());
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  private class TestIndentOptionsProvider extends FileIndentOptionsProvider {
    @Nullable
    @Override
    public CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull Project project, @NotNull CodeStyleSettings settings, @NotNull VirtualFile file) {
      return myTestIndentOptions;
    }

    @Override
    public boolean useOnFullReformat() {
      return myUseOnFullReformat;
    }
  }

  @Test
  public void testTypeEnter() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestTyping('\n');
  }

  @Test
  public void testTypeTab() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestTyping('\t');
  }

  @Test
  public void testIndentSelection() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestAction(new IndentSelectionAction());
  }

  @Test
  public void testUnindentSelection() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestAction(new UnindentSelectionAction());
  }

  @Test
  public void testReformatFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      CodeStyleManager.getInstance(myFixture.getProject()).reformat(file);
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
    });
  }

  @Test
  public void testReformatFileSupported() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myUseOnFullReformat = true;
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
                                               CodeStyleManager.getInstance(myFixture.getProject()).reformat(file);
                                             });
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  @Test
  public void testReformatText() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> CodeStyleManager.getInstance(myFixture.getProject()).reformatText(file, 0, file.getTextRange().getEndOffset()));
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  /**
   * Reformat using indent provider if a part of the file is selected.
   */
  @Test
  public void testReformatTextRange() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    // Just any range smaller than the file
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> CodeStyleManager.getInstance(myFixture.getProject()).reformatText(file, 6, file.getTextRange().getEndOffset() - 1));
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  @Test
  public void testReformatTextFullSupported() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myUseOnFullReformat = true;
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> CodeStyleManager.getInstance(myFixture.getProject()).reformatText(file, 0, file.getTextRange().getEndOffset()));
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }
}
