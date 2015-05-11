package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.LightRefactoringTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineConstantFieldTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testQualifiedExpression() throws Exception {
    doTest();
  }

  public void testQualifiedConstantExpression() throws Exception {
    doTest();
  }

   public void testQualifiedConstantExpressionReplacedWithAnotherOne() throws Exception {
    doTest();
  }
  
  public void testStaticallyImportedQualifiedExpression() throws Exception {
    doTest();
  }

  public void testCastWhenLambdaAsQualifier() throws Exception {
    doTest();
  }

  public void testConstantFromLibrary() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineConstantField/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(fileName + ".after");
  }

  private void performAction() {
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiField);
    PsiField field = (PsiField)element.getNavigationElement();
    new InlineConstantFieldProcessor(field, getProject(), refExpr, element instanceof PsiCompiledElement).run();
  }
}