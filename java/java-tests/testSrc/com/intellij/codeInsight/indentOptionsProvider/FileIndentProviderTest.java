/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.indentOptionsProvider;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.actions.IndentSelectionAction;
import com.intellij.openapi.editor.actions.UnindentSelectionAction;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Rustam Vishnyakov
 */
public class FileIndentProviderTest extends LightPlatformCodeInsightFixtureTestCase {

  private final static FileIndentOptionsProvider TEST_FILE_INDENT_OPTIONS_PROVIDER = new TestIndentOptionsProvider();
  private static CommonCodeStyleSettings.IndentOptions myTestIndentOptions;
  private static boolean myUseOnFullReformat;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionPoint<FileIndentOptionsProvider> extensionPoint =
      Extensions.getRootArea().getExtensionPoint(FileIndentOptionsProvider.EP_NAME);
    extensionPoint.registerExtension(TEST_FILE_INDENT_OPTIONS_PROVIDER);
    myTestIndentOptions = new CommonCodeStyleSettings.IndentOptions();
  }

  @Override
  protected void tearDown() throws Exception {
    ExtensionPoint<FileIndentOptionsProvider> extensionPoint =
      Extensions.getRootArea().getExtensionPoint(FileIndentOptionsProvider.EP_NAME);
    extensionPoint.unregisterExtension(TEST_FILE_INDENT_OPTIONS_PROVIDER);
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

  private static class TestIndentOptionsProvider extends FileIndentOptionsProvider {
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
    CodeStyleManager.getInstance(getProject()).reformat(file);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testReformatFileSupported() {
    myUseOnFullReformat = true;
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    CodeStyleManager.getInstance(getProject()).reformat(file);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testReformatText() {
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    CodeStyleManager.getInstance(getProject()).reformatText(file, 0, file.getTextRange().getEndOffset());
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
    CodeStyleManager.getInstance(getProject()).reformatText(file, 6, file.getTextRange().getEndOffset() - 1);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testReformatTextFullSupported() {
    myUseOnFullReformat = true;
    myTestIndentOptions.INDENT_SIZE = 3;
    myTestIndentOptions.TAB_SIZE = 2;
    myTestIndentOptions.USE_TAB_CHARACTER = true;
    PsiFile file = myFixture.configureByFile(getTestName(true) + "_before.java");
    CodeStyleManager.getInstance(getProject()).reformatText(file, 0, file.getTextRange().getEndOffset());
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }
}
