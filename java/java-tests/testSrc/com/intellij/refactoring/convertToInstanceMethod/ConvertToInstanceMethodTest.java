package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.LightRefactoringTestCase;
import com.intellij.util.VisibilityUtil;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodTest extends LightRefactoringTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple() throws Exception { doTest(0); }

  public void testInterface() throws Exception { doTest(1); }

  public void testInterfacePrivate() throws Exception { doTest(1); }

  public void testInterface2() throws Exception { doTest(0); }

  public void testInterface3() throws Exception { doTest(0); }

  public void testTypeParameter() throws Exception { doTest(0); }

  public void testInterfaceTypeParameter() throws Exception { doTest(0); }

  private void doTest(final int targetParameter) throws Exception {
    final String filePath = "/refactoring/convertToInstanceMethod/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    new ConvertToInstanceMethodProcessor(getProject(),
                                         method, method.getParameterList().getParameters()[targetParameter], VisibilityUtil.ESCALATE_VISIBILITY).run();
    checkResultByFile(filePath + ".after");

  }
}
