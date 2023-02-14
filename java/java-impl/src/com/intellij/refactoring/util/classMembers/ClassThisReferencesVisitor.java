// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * Visits explicit and implicit references to 'this'
 */
public abstract class ClassThisReferencesVisitor extends ClassMemberReferencesVisitor {
  HashSet<PsiClass> myClassSuperClasses;
  public ClassThisReferencesVisitor(PsiClass aClass) {
    super(aClass);
    myClassSuperClasses = new HashSet<>();
    myClassSuperClasses.add(aClass);
  }

  @Override public void visitThisExpression(@NotNull PsiThisExpression expression) {
    PsiJavaCodeReferenceElement ref = expression.getQualifier();
    if(ref != null) {
      PsiElement element = ref.resolve();
      if(element instanceof PsiClass aClass) {
        if(myClassSuperClasses.contains(aClass)) {
          visitExplicitThis(aClass, expression);
        }
        if(aClass.isInheritor(getPsiClass(), true)) {
          myClassSuperClasses.add(aClass);
          visitExplicitThis(aClass, expression);
        }
      }
      ref.accept(this);
    }
    else {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if(containingClass != null) {
        if(getPsiClass().getManager().areElementsEquivalent(getPsiClass(), containingClass)) {
          visitExplicitThis(getPsiClass(), expression);
        }
      }
    }
  }

  @Override public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
    PsiJavaCodeReferenceElement ref = expression.getQualifier();
    if (ref != null) {
      PsiElement element = ref.resolve();
      if (element instanceof PsiClass aClass) {
        if (myClassSuperClasses.contains(aClass)) {
          visitExplicitSuper(aClass.getSuperClass(), expression);
        }
        if (aClass.isInheritor(getPsiClass(), true)) {
          myClassSuperClasses.add(aClass);
          visitExplicitSuper(aClass.getSuperClass(), expression);
        }
      }
      ref.accept(this);
    }
    else {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (containingClass != null) {
        if (getPsiClass().getManager().areElementsEquivalent(getPsiClass(), containingClass)) {
          visitExplicitSuper(getPsiClass().getSuperClass(), expression);
        }
      }
    }
  }

  protected abstract void visitExplicitThis(PsiClass referencedClass, PsiThisExpression reference);
  protected abstract void visitExplicitSuper(PsiClass referencedClass, PsiSuperExpression reference);
}
