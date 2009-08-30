package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.*;

public interface ClassReferenceVisitor {
  class TypeOccurence {
    public TypeOccurence(PsiElement element, PsiType outermostType) {
      this.element = element;
      this.outermostType = outermostType;
    }

    public final PsiElement element;
    public final PsiType outermostType;
  }

  void visitReferenceExpression(PsiReferenceExpression referenceExpression);

  void visitLocalVariableDeclaration(PsiLocalVariable variable, TypeOccurence occurence);
  void visitFieldDeclaration(PsiField field, TypeOccurence occurence);
  void visitParameterDeclaration(PsiParameter parameter, TypeOccurence occurence);
  void visitMethodReturnType(PsiMethod method, TypeOccurence occurence);
  void visitTypeCastExpression(PsiTypeCastExpression typeCastExpression, TypeOccurence occurence);

  void visitNewExpression(PsiNewExpression newExpression, TypeOccurence occurence);

  void visitOther(PsiElement ref);
}
