// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.indentOptionsProvider;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.actions.IndentSelectionAction;
import com.intellij.openapi.editor.actions.UnindentSelectionAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class FileIndentProviderTest extends BasePlatformTestCase {
  private final FileIndentOptionsProvider TEST_FILE_INDENT_OPTIONS_PROVIDER = new TestIndentOptionsProvider();
  private CommonCodeStyleSettings.IndentOptions myTestIndentOptions;
  private boolean myUseOnFullReformat;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    FileIndentOptionsProvider.EP_NAME.getPoint().registerExtension(TEST_FILE_INDENT_OPTIONS_PROVIDER, getTestRootDisposable());
    myTestIndentOptions = new CommonCodeStyleSettings.IndentOptions();
  }

  @Override
  protected void tearDown() throws Exception {
    myTestIndentOptions = null;
    myUseOnFullReformat = false;
    super.tearDown();
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/codeInsight/indentProvider";
  }

  private void doTestTyping(char c) {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    myFixture.type(c);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  private void doTestAction(@NotNull AnAction action) {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    assertTrue(myFixture.testAction(action).isEnabled());
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  private class TestIndentOptionsProvider extends FileIndentOptionsProvider {
    @Nullable
    @Override
    public CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
      return myTestIndentOptions;
    }

    @Override
    public boolean useOnFullReformat() {
      return myUseOnFullReformat;
    }
  }

  public void testTypeEnter() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestTyping('\n');
  }

  public void testTypeTab() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestTyping('\t');
  }

  public void testIndentSelection() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestAction(new IndentSelectionAction());
  }

  public void testUnindentSelection() {
    myTestIndentOptions.INDENT_SIZE = 3;
    doTestAction(new UnindentSelectionAction());
  }

  public void testReformatFile() {
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      CodeStyleManager.getInstance(getProject()).reformat(file);
      myFixture.checkResultByFile(getTestName(true) + "_after.java");
    });
  }

  public void testReformatFileSupported() {
    myUseOnFullReformat = true;
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                                               CodeStyleManager.getInstance(getProject()).reformat(file);
                                             });
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testReformatText() {
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> CodeStyleManager.getInstance(getProject()).reformatText(file, 0, file.getTextRange().getEndOffset()));
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  /**
   * Reformat using indent provider if a part of the file is selected.
   */
  public void testReformatTextRange() {
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    // Just any range smaller than the file
    WriteCommandAction.runWriteCommandAction(getProject(), () -> CodeStyleManager.getInstance(getProject()).reformatText(file, 6, file.getTextRange().getEndOffset() - 1));
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testReformatTextFullSupported() {
    myUseOnFullReformat = true;
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> CodeStyleManager.getInstance(getProject()).reformatText(file, 0, file.getTextRange().getEndOffset()));
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }
}
