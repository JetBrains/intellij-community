/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class ExtractMethodObjectTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(boolean createInnerClass) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/extractMethodObject/" + testName + ".java");
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;
    
    final ExtractMethodObjectProcessor processor =
        new ExtractMethodObjectProcessor(getProject(), getEditor(), method.getBody().getStatements(), "InnerClass");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    extractProcessor.setShowErrorDialogs(false);
    extractProcessor.prepare();
    extractProcessor.testRun();
    processor.setCreateInnerClass(createInnerClass);
    processor.run();
    if (createInnerClass) {
      processor.moveUsedMethodsToInner();
    }
    DuplicatesImpl.processDuplicates(extractProcessor, getProject(), getEditor());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    processor.getMethod().delete();
    checkResultByFile("/refactoring/extractMethodObject/" + testName + ".java" + ".after");
  }

  public void testStatic() throws Exception {
    doTest();
  }

  public void testStaticTypeParams() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsReturn() throws Exception {
    doTest();
  }

  public void testTypeParamsReturn() throws Exception {
    doTest();
  }

  public void testTypeParams() throws Exception {
    doTest();
  }

  public void testMethodInHierarchy() throws Exception {
    doTest();
  }

  public void testQualifier() throws Exception {
    doTest();
  }

  public void testVarargs() throws Exception {
    doTest();
  }

  public void testFieldUsage() throws Exception {
    doTest();
  }

  public void testMethodInHierarchyReturn() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsReturnNoDelete() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsRecursive() throws Exception {
    doTest();
  }

  public void testRecursion() throws Exception {
    doTest();
  }

  public void testWrapWithObject() throws Exception {
    doTest(false);
  }

  public void testWrapWithObjectRecursive() throws Exception {
    doTest(false);
  }
  
  public void testWithPrivateMethodUsed() throws Exception {
    doTest();
  }

  public void testWithPrivateMethodWhichCantBeMoved() throws Exception {
    doTest();
  }
}