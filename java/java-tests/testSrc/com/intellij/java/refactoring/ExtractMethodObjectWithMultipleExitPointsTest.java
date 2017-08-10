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

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObjectWithMultipleExitPointsTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean createInnerClass) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java");
    int startOffset = myEditor.getSelectionModel().getSelectionStart();
    int endOffset = myEditor.getSelectionModel().getSelectionEnd();

    final PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(myFile, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(myFile, startOffset, endOffset);
    }

    final ExtractMethodObjectProcessor processor =
      new ExtractMethodObjectProcessor(getProject(), getEditor(), elements, "Inner");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    extractProcessor.setShowErrorDialogs(false);
    extractProcessor.prepare();
    extractProcessor.testPrepare();
    processor.setCreateInnerClass(createInnerClass);


    ExtractMethodObjectHandler.run(getProject(), getEditor(), processor, extractProcessor);


    checkResultByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java" + ".after");
  }

  public void testStaticInner() throws Exception {
    doTest();
  }

  public void testInputOutput() throws Exception {
    doTest();
  }

  public void testOutputVarsReferences() throws Exception {
    doTest();
  }

  public void testMultilineDeclarations() throws Exception {
    doTest();
  }

  public void testMultilineDeclarationsWithReturn() throws Exception {
    doTest();
  }

  public void testConditionalExit() throws Exception {
    doTest();
  }

  public void testOutputVariable() throws Exception {
    doTest();
  }

  public void testUniqueObjectName() throws Exception {
    doTest();
  }

  public void testExtractedAssignmentExpression() throws Exception {
    doTest();
  }

  public void testExtractedIncExpression() throws Exception {
    doTest();
  }


  public void testWithInnerClasses() throws Exception {
    doTest();
  }

  public void testNonCanonicalNaming() throws Exception {
    doTest();
  }

  public void testExtractFromAnonymous() throws Exception {
    doTest();
  }

  public void testExtractFromIfStatementInsideAnonymous() throws Exception {
    doTest();
  }

  public void testConditionalExitWithoutCodeBlock() throws Exception {
    doTest();
  }

  public void testReturnExitStatement() throws Exception {
    doTest();
  }

  public void testFromStaticContext() throws Exception {
    doTest();
  }

  public void testBatchUpdateCausedByFormatter() throws Exception {
    doTest();
  }

  public void testFormattingInside() throws Exception {
    doTest();
  }

  public void testAssignReturnValueToForeachParameter() throws Exception {
    doTest();
  }

  public void testRenameInInitializer() throws Exception {
    doTestWithIdeaCodeStyleSettings();
  }

  public void testSameFieldsWithPrefix() throws Exception {
    doTestWithIdeaCodeStyleSettings();
  }

  public void testOutputVariablesUsedInLoop1() throws Exception {
    doTest();
  }

  public void testOutputVariablesUsedInLoop2() throws Exception {
    doTest();
  }

  private void doTestWithIdeaCodeStyleSettings() throws Exception {
    final JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    boolean oldPrefer = settings.PREFER_LONGER_NAMES;
    settings.PREFER_LONGER_NAMES = false;
    try {
      doTest();
    }
    finally {
      settings.FIELD_NAME_PREFIX = oldPrefix;
      settings.PREFER_LONGER_NAMES = oldPrefer;
    }
  }
}
