package com.intellij.refactoring.moveMethod;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.JavaTestUtil;
import com.intellij.util.VisibilityUtil;

/**
 * @author ven
 */
public class MoveInstanceMethodTest extends LightCodeInsightTestCase {

  public void testSimple() throws Exception { doTest(true, 0); }

  public void testSimpleWithTargetField() throws Exception { doTest(false, 1); }

  public void testInterface() throws Exception { doTest(true, 0); }

  public void testWithInner() throws Exception { doTest(true, 0); }

  public void testJavadoc() throws Exception { doTest(true, 0); }

  public void testRecursive() throws Exception { doTest(true, 0); }

  public void testRecursive1() throws Exception { doTest(true, 0); }

  public void testQualifiedThis() throws Exception { doTest(true, 0); }

  public void testQualifyThisHierarchy() throws Exception {doTest(true, 0);}

  public void testTwoParams() throws Exception { doTest(true, 0); }

  public void testNoThisParam() throws Exception { doTest(false, 0); }

  public void testNoGenerics() throws Exception { doTest(false, 0); }

  public void testQualifierToArg1() throws Exception { doTest(true, 0); }

  public void testQualifierToArg2() throws Exception { doTest(true, 0); }

  public void testQualifierToArg3() throws Exception { doTest(true, 0); }

  public void testIDEADEV11257() throws Exception { doTest(true, 0); }

  public void testEscalateVisibility() throws Exception {
    doTest(true, 0, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testSameNames() throws Exception {
    doTest(true, 0);
  }
  
  public void testSameNamesRecursion() throws Exception {
    doTest(true, 0);
  }

  public void testQualifyFieldAccess() throws Exception {
    doTest(false, 0);
  }

  public void testStripFieldQualifier() throws Exception {
    doTest(false, 0);
  }

  private void doTest(boolean isTargetParameter, final int targetIndex) throws Exception {
    doTest(isTargetParameter, targetIndex, null);
  }

  private void doTest(boolean isTargetParameter, final int targetIndex, final String newVisibility) throws Exception {
    final String filePath = "/refactoring/moveInstanceMethod/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    final PsiVariable targetVariable = isTargetParameter ? method.getParameterList().getParameters()[targetIndex] :
                                       method.getContainingClass().getFields()[targetIndex];
    new MoveInstanceMethodProcessor(getProject(),
                                    method, targetVariable, newVisibility, MoveInstanceMethodHandler.suggestParameterNames (method, targetVariable)).run();
    checkResultByFile(filePath + ".after");

  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

}
