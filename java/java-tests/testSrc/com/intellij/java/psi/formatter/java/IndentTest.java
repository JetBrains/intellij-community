/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class IndentTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/formatter/indent";

  private CommonCodeStyleSettings getJavaSettings() {
    return  CodeStyle.getSettings(codeInsightFixture.get().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  @Test
  public void testSCR3681() throws Exception {
    doTest("SCR3681.java", "SCR3681_after.java");
  }

  @Test
  public void testBinaryOperation() throws Exception {

    defaultSettings();
   CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTest("BinaryOperation.java", "BinaryOperation_after.java");
  }

  @Test
  public void testBinaryOperationSignMoved() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTest("BinaryOperationSignMoved.java", "BinaryOperationSignMoved_after.java");
  }

  @Test
  public void testTernaryOperation() throws Exception {
    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;

    doTest("TernaryOperation.java", "TernaryOperation_after.java");
  }

  @Test
  public void testTernaryOperationSignMoved() throws Exception {
    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;

    doTest("TernaryOperationSignMoved.java", "TernaryOperationSignMoved_after.java");
  }

  @Test
  public void testImplementsList() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;

    doTest("ImplementsList.java", "ImplementsList_after.java");
  }

  @Test
  public void testImplementsList2() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;

    doTest("ImplementsList2.java", "ImplementsList2_after.java");
  }

  @Test
  public void testParenthesizedBinop() throws Exception {
    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;

    doTest("ParenthesizedBinop.java", "ParenthesizedBinop_after.java");
  }

  @Test
  public void testParenthesizedContinuation() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;

    doTest("ParenthesizedContinuation.java", "ParenthesizedContinuation_after.java");
  }

  @Test
  public void testParenthesizedContinuation2() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;

    doTest("ParenthesizedContinuation2.java", "ParenthesizedContinuation2_after.java");
  }

  @Test
  public void testDoNotIndentTopLevelClassMembers() throws Exception {

    defaultSettings();
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = true;

    doTest("DoNotIndentTopLevelClassMembers.java", "DoNotIndentTopLevelClassMembers_after.java");
  }

  private void doTest(String fileNameBefore, String fileNameAfter) throws Exception {
    String text = loadFile(fileNameBefore);
    Project project = codeInsightFixture.get().getProject();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileNameBefore);
    final PsiFile file = ReadAction.computeBlocking(
      () -> PsiFileFactory.getInstance(project).createFileFromText(fileNameBefore, fileType, text, LocalTimeCounter.currentTime(), true, false));
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        CodeStyleManager.getInstance(project).reformat(file);
      }
      catch (IncorrectOperationException e) {
        throw new AssertionError(e);
      }
    }), null, null));


    String textAfter = loadFile(fileNameAfter);
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    textAfter = StringUtil.trimStart(textAfter, "\n");
    fileText = StringUtil.trimStart(fileText, "\n");

    assertEquals(textAfter, fileText);
  }

  private static String loadFile(String name) throws Exception {
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

  @AfterEach
  void tearDown() {
    defaultSettings();
  }
}
