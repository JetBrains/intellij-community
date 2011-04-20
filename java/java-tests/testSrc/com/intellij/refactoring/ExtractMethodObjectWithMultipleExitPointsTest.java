/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class ExtractMethodObjectWithMultipleExitPointsTest extends LightCodeInsightTestCase {
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

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final ExtractMethodObjectProcessor processor =
          new ExtractMethodObjectProcessor(getProject(), getEditor(), elements, "Inner");
        final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
        extractProcessor.setShowErrorDialogs(false);
        extractProcessor.prepare();
        extractProcessor.testRun();
        processor.setCreateInnerClass(createInnerClass);
        processor.run();
        DuplicatesImpl.processDuplicates(extractProcessor, getProject(), getEditor());
        processor.getMethod().delete();
      }
    }.execute().throwException();

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
}
