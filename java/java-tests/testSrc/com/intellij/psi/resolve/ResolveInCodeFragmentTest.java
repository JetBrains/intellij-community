package com.intellij.psi.resolve;

import com.intellij.psi.*;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ResolveTestCase;

/**
 * @author max
 */
public class ResolveInCodeFragmentTest extends ResolveTestCase {
  public void testLocalVariable() throws Exception {
    final PsiReference iRef = configure();

    PsiElement context = PsiTreeUtil.getParentOfType(iRef.getElement(), PsiCodeBlock.class);
    JavaCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myProject)
      .createExpressionCodeFragment(iRef.getElement().getText(), context, null, true);
    codeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

    PsiElement[] fileContent = codeFragment.getChildren();
    assertEquals(1, fileContent.length);
    assertTrue(fileContent[0] instanceof PsiExpression);

    PsiExpression expr = (PsiExpression) fileContent[0];
    expr.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        assertEquals(iRef.resolve(),
                     expression.resolve());
      }
    });
  }

  public void testjavaLangClass() throws Exception {
    PsiCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment(
          "Boolean.getBoolean(\"true\")", null, null, true);

    PsiElement[] fileContent = codeFragment.getChildren();
    assertEquals(1, fileContent.length);
    assertTrue(fileContent[0] instanceof PsiExpression);

    PsiExpression expr = (PsiExpression) fileContent[0];
    assertNotNull(expr.getType());
    assertEquals("boolean", expr.getType().getCanonicalText());
  }

  private PsiReference configure() throws Exception {
    return configureByFile("codeFragment/" + getTestName(false) + ".java");
  }

  public void testResolveScopeWithFragmentContext() throws Exception {
    PsiElement physical = configureByFile("codeFragment/LocalVariable.java").getElement();
    JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject)
      .createExpressionCodeFragment("ref", physical, null, true);
    fragment.forceResolveScope(new JavaSourceFilterScope(physical.getResolveScope()));
    assertFalse(fragment.getResolveScope().equals(physical.getResolveScope()));

    PsiExpression lightExpr = JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("xxx.xxx", fragment);
    assertEquals(lightExpr.getResolveScope(), fragment.getResolveScope());
  }
}
