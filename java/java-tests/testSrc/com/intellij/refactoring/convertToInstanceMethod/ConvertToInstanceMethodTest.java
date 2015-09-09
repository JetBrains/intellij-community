package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightRefactoringTestCase;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodTest extends LightRefactoringTestCase {
  @NotNull
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

  public void testJavadocParameter() throws Exception { doTest(0); }

  public void testConflictingParameterName() throws Exception {
    doTest(0);
  }

  public void testVisibilityConflict() throws Exception {
    try {
      doTest(0, PsiModifier.PRIVATE);
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>Test.foo(Bar)</code></b> is private and will not be accessible from instance initializer of class class <b><code>Test</code></b>.", e.getMessage()); 
    }
  }

  private void doTest(final int targetParameter) throws Exception {
    doTest(targetParameter, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  private void doTest(final int targetParameter, final String visibility) throws Exception {
    final String filePath = "/refactoring/convertToInstanceMethod/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    new ConvertToInstanceMethodProcessor(getProject(),
                                         method, method.getParameterList().getParameters()[targetParameter],
                                         visibility).run();
    checkResultByFile(filePath + ".after");

  }
}
