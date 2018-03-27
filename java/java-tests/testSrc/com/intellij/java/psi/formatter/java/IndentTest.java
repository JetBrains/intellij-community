/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.formatter.java;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

public class IndentTest extends LightIdeaTestCase {
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/formatter/indent";

  private static CommonCodeStyleSettings getJavaSettings() {
    return  CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testSCR3681() throws Exception {
    doTest("SCR3681.java", "SCR3681_after.java");
  }

  public void testBinaryOperation() throws Exception {

    defaultSettings();
   CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTest("BinaryOperation.java", "BinaryOperation_after.java");
  }

  public void testBinaryOperationSignMoved() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTest("BinaryOperationSignMoved.java", "BinaryOperationSignMoved_after.java");
  }

  public void testTernaryOperation() throws Exception {
    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;

    doTest("TernaryOperation.java", "TernaryOperation_after.java");
  }

  public void testTernaryOperationSignMoved() throws Exception {
    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;

    doTest("TernaryOperationSignMoved.java", "TernaryOperationSignMoved_after.java");
  }

  public void testImplementsList() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;

    doTest("ImplementsList.java", "ImplementsList_after.java");
  }

  public void testImplementsList2() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;

    doTest("ImplementsList2.java", "ImplementsList2_after.java");
  }

  public void testParenthesizedBinop() throws Exception {
    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;

    doTest("ParenthesizedBinop.java", "ParenthesizedBinop_after.java");
  }

  public void testParenthesizedContinuation() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;

    doTest("ParenthesizedContinuation.java", "ParenthesizedContinuation_after.java");
  }

  public void testParenthesizedContinuation2() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;

    doTest("ParenthesizedContinuation2.java", "ParenthesizedContinuation2_after.java");
  }

  public void testDoNotIndentTopLevelClassMembers() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = true;

    doTest("DoNotIndentTopLevelClassMembers.java", "DoNotIndentTopLevelClassMembers_after.java");
  }

  private void doTest(String fileNameBefore, String fileNameAfter) throws Exception {
    String text = loadFile(fileNameBefore);
    final PsiFile file = createFile(fileNameBefore, text);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        CodeStyleManager.getInstance(getProject()).reformat(file);
      }
      catch (IncorrectOperationException e) {
        assertTrue(false);
      }
    }), null, null);


    String textAfter = loadFile(fileNameAfter);
    String fileText = file.getText();
    textAfter = StringUtil.trimStart(textAfter, "\n");
    fileText = StringUtil.trimStart(fileText, "\n");

    assertEquals(textAfter, fileText);
  }

  private String loadFile(String name) throws Exception {
    String fullName = BASE_PATH + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

  private void defaultSettings() {
    CommonCodeStyleSettings settings = getJavaSettings();

    settings.ALIGN_MULTILINE_PARAMETERS = true;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
    settings.ALIGN_MULTILINE_FOR = true;

    settings.ALIGN_MULTILINE_BINARY_OPERATION = false;
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = false;
    settings.ALIGN_MULTILINE_THROWS_LIST = false;
    settings.ALIGN_MULTILINE_EXTENDS_LIST = false;
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
    settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = false;
  }

  @Override
  protected void tearDown() throws Exception {
    defaultSettings();
    super.tearDown();
  }
}
