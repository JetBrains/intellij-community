// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public abstract class ClassMemberReferencesVisitor extends JavaRecursiveElementWalkingVisitor {
  private final PsiClass myClass;

  public ClassMemberReferencesVisitor(PsiClass aClass) {
    myClass = aClass;
  }

  @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
      qualifier.accept(this);
      if (!(qualifier instanceof PsiReferenceExpression)
          || !(((PsiReferenceExpression) qualifier).resolve() instanceof PsiClass)) {
        return;
      }
    }

    PsiElement referencedElement = expression.resolve();

    if (referencedElement instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember)referencedElement).getContainingClass();
      if (isPartOf(myClass, containingClass)) {
        visitClassMemberReferenceExpression((PsiMember)referencedElement, expression);
      }
    }
  }

  private static boolean isPartOf(PsiClass elementClass, PsiClass containingClass) {
    if (containingClass == null) return false;
    if (elementClass.equals(containingClass) || elementClass.isInheritor(containingClass, true)) {
      return true;
    } else {
      return PsiTreeUtil.isAncestor(containingClass, elementClass, true);
    }
  }

  protected void visitClassMemberReferenceExpression(PsiMember classMember,
                                                     PsiReferenceExpression classMemberReferenceExpression) {
    visitClassMemberReferenceElement(classMember, classMemberReferenceExpression);
  }

  protected abstract void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference);

  @Override public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    PsiElement referencedElement = reference.resolve();
    if (referencedElement instanceof PsiClass referencedClass) {
      if (PsiTreeUtil.isAncestor(myClass, referencedClass, true)) {
        visitClassMemberReferenceElement(referencedClass, reference);
      }
      else if (isPartOf(myClass, referencedClass.getContainingClass())) {
        visitClassMemberReferenceElement(referencedClass, reference);
      }
    }
  }

  protected final PsiClass getPsiClass() {
    return myClass;
  }
}
