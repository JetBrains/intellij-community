package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.*;

public final class CommentMatchingStrategy extends MatchingStrategyBase {
  @Override public void visitClass(final PsiClass clazz) {
    result = true;
  }

  @Override public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  @Override public void visitMethod(final PsiMethod method) {
    result = true;
  }

  @Override public void visitComment(final PsiComment comment) {
    result = true;
  }

  @Override public void visitExpression(PsiExpression expression) {
    result = true;
  }

  @Override public void visitExpressionList(PsiExpressionList list) {
    result = true;
  }

  @Override public void visitVariable(PsiVariable variable) {
    result = true;
  }

  @Override public void visitModifierList(PsiModifierList list) {
    result = true;
  }

  @Override public void visitParameterList(PsiParameterList list) {
    result = true;
  }

  @Override public void visitReferenceList(PsiReferenceList list) {
    result = true;
  }

  @Override public void visitReferenceParameterList(PsiReferenceParameterList list) {
    result = true;
  }

  @Override public void visitTypeElement(PsiTypeElement type) {
    result = true;
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    result = true;
  }

  @Override public void visitTypeParameterList(PsiTypeParameterList list) {
    result = true;
  }

  @Override public void visitResourceList(PsiResourceList resourceList) {
    result = true;
  }

  @Override public void visitAnnotation(PsiAnnotation annotation) {
    result = true;
  }

  @Override public void visitAnnotationParameterList(PsiAnnotationParameterList list) {
    result = true;
  }

  @Override public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    result = true;
  }

  @Override public void visitNameValuePair(PsiNameValuePair pair) {
    result = true;
  }

  private CommentMatchingStrategy() {}

  private static class CommentMatchingStrategyHolder {
    private static final CommentMatchingStrategy instance = new CommentMatchingStrategy();
  }

  public static MatchingStrategy getInstance() {
    return CommentMatchingStrategyHolder.instance;
  }
}
