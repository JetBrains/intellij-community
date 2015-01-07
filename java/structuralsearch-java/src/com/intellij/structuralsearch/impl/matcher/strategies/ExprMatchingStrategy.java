package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.*;

/**
 * Expression matching strategy
 */
public class ExprMatchingStrategy extends MatchingStrategyBase {

  @Override
  public void visitTypeElement(PsiTypeElement type) {
    result = true;
  }

  @Override public void visitReferenceParameterList(PsiReferenceParameterList psiReferenceParameterList) {
    result = true;
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = true;
  }

  @Override public void visitTypeParameterList(PsiTypeParameterList psiTypeParameterList) {
    result = true;
  }

  @Override public void visitReferenceList(final PsiReferenceList list) {
    result = true;
  }

  @Override public void visitAnnotation(final PsiAnnotation annotation) {
    result = true;
  }

  @Override public void visitAnnotationParameterList(final PsiAnnotationParameterList list) {
    result = true;
  }

  @Override public void visitModifierList(final PsiModifierList list) {
    result = true;
  }

  @Override public void visitNameValuePair(final PsiNameValuePair pair) {
    result = true;
  }

  @Override public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    result = true;
  }

  @Override public void visitExpression(final PsiExpression expr) {
    result = true;
  }

  @Override public void visitVariable(final PsiVariable field) {
    result = true;
  }

  @Override public void visitClass(final PsiClass clazz) {
    result = true;
  }

  @Override public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  @Override public void visitMethod(final PsiMethod method) {
    result = true;
  }

  @Override public void visitExpressionList(final PsiExpressionList list) {
    result = true;
  }

  @Override public void visitJavaFile(final PsiJavaFile file) {
    result = true;
  }

  @Override
  public void visitPackageStatement(PsiPackageStatement statement) {
    result = true;
  }

  // finding parameters
  @Override public void visitParameterList(final PsiParameterList list) {
    result = true;
  }

  protected ExprMatchingStrategy() {}

  private static class ExprMatchingStrategyHolder {
    private static final ExprMatchingStrategy instance = new ExprMatchingStrategy();
  }

  public static MatchingStrategy getInstance() {
    return ExprMatchingStrategyHolder.instance;
  }
}
