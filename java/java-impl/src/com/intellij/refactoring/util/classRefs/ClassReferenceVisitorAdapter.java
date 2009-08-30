package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.*;

/**
 * ClassReferenceVisitor that does nothing.
 * @author dsl
 */
public class ClassReferenceVisitorAdapter implements ClassReferenceVisitor {
  public static final ClassReferenceVisitorAdapter INSTANCE = new ClassReferenceVisitorAdapter();

  public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
  }

  public void visitLocalVariableDeclaration(PsiLocalVariable variable, ClassReferenceVisitor.TypeOccurence occurence) {
  }

  public void visitFieldDeclaration(PsiField field, ClassReferenceVisitor.TypeOccurence occurence) {
  }

  public void visitParameterDeclaration(PsiParameter parameter, ClassReferenceVisitor.TypeOccurence occurence) {
  }

  public void visitMethodReturnType(PsiMethod method, ClassReferenceVisitor.TypeOccurence occurence) {
  }

  public void visitTypeCastExpression(PsiTypeCastExpression typeCastExpression, ClassReferenceVisitor.TypeOccurence occurence) {
  }

  public void visitNewExpression(PsiNewExpression newExpression, ClassReferenceVisitor.TypeOccurence occurence) {
  }

  public void visitOther(PsiElement ref) {
  }

}
