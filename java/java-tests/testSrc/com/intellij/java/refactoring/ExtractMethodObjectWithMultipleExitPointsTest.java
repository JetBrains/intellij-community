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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class ExtractMethodObjectWithMultipleExitPointsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean createInnerClass) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java");
    myFixture.runInCommand(() -> {
      int startOffset = myFixture.getEditor().getSelectionModel().getSelectionStart();
      int endOffset = myFixture.getEditor().getSelectionModel().getSelectionEnd();

      final PsiElement[] elements;
      PsiExpression expr = CodeInsightUtil.findExpressionInRange(myFixture.getFile(), startOffset, endOffset);
      if (expr != null) {
        elements = new PsiElement[]{expr};
      }
      else {
        elements = CodeInsightUtil.findStatementsInRange(myFixture.getFile(), startOffset, endOffset);
      }

      final ExtractMethodObjectProcessor processor =
        new ExtractMethodObjectProcessor(myFixture.getProject(), myFixture.getEditor(), elements, "Inner");
      final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
      extractProcessor.setShowErrorDialogs(false);
      extractProcessor.prepare();
      extractProcessor.testPrepare();
      processor.setCreateInnerClass(createInnerClass);


      ExtractMethodObjectHandler.extractMethodObject(myFixture.getProject(), myFixture.getEditor(), processor, extractProcessor);


      myFixture.checkResultByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java" + ".after");
    });
  }

  @Test
  public void testStaticInner() throws Exception {
    doTest();
  }

  @Test
  public void testInputOutput() throws Exception {
    doTest();
  }

  @Test
  public void testOutputVarsReferences() throws Exception {
    doTest();
  }

  @Test
  public void testMultilineDeclarations() throws Exception {
    doTest();
  }

  @Test
  public void testMultilineDeclarationsWithReturn() throws Exception {
    doTest();
  }

  @Test
  public void testConditionalExit() throws Exception {
    doTest();
  }

  @Test
  public void testOutputVariable() throws Exception {
    doTest();
  }

  @Test
  public void testUniqueObjectName() throws Exception {
    doTest();
  }

  @Test
  public void testExtractedAssignmentExpression() throws Exception {
    doTest();
  }

  @Test
  public void testExtractedIncExpression() throws Exception {
    doTest();
  }


  @Test
  public void testWithInnerClasses() throws Exception {
    doTest();
  }

  @Test
  public void testNonCanonicalNaming() throws Exception {
    doTest();
  }

  @Test
  public void testExtractFromAnonymous() throws Exception {
    doTest();
  }

  @Test
  public void testExtractFromIfStatementInsideAnonymous() throws Exception {
    doTest();
  }

  @Test
  public void testConditionalExitWithoutCodeBlock() throws Exception {
    doTest();
  }

  @Test
  public void testReturnExitStatement() throws Exception {
    doTest();
  }

  @Test
  public void testFromStaticContext() throws Exception {
    doTest();
  }

  @Test
  public void testBatchUpdateCausedByFormatter() throws Exception {
    doTest();
  }

  @Test
  public void testFormattingInside() throws Exception {
    doTest();
  }

  @Test
  public void testAssignReturnValueToForeachParameter() throws Exception {
    doTest();
  }

  @Test
  public void testRenameInInitializer() throws Exception {
    doTestWithIdeaCodeStyleSettings();
  }

  @Test
  public void testSameFieldsWithPrefix() throws Exception {
    doTestWithIdeaCodeStyleSettings();
  }

  @Test
  public void testOutputVariablesUsedInLoop1() throws Exception {
    doTest();
  }

  @Test
  public void testOutputVariablesUsedInLoop2() throws Exception {
    doTest();
  }

  private void doTestWithIdeaCodeStyleSettings() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.FIELD_NAME_PREFIX = "my";
    settings.PREFER_LONGER_NAMES = false;
    doTest();
  }
}
