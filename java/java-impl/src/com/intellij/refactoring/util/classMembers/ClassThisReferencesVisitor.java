/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.HashSet;

/**
 * Visits explicit and implicit references to 'this'
 * @author dsl
 */
public abstract class ClassThisReferencesVisitor extends ClassMemberReferencesVisitor {
  HashSet<PsiClass> myClassSuperClasses;
  public ClassThisReferencesVisitor(PsiClass aClass) {
    super(aClass);
    myClassSuperClasses = new HashSet<>();
    myClassSuperClasses.add(aClass);
  }

  @Override public void visitThisExpression(PsiThisExpression expression) {
    PsiJavaCodeReferenceElement ref = expression.getQualifier();
    if(ref != null) {
      PsiElement element = ref.resolve();
      if(element instanceof PsiClass) {
        PsiClass aClass = (PsiClass) element;
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

  @Override public void visitSuperExpression(PsiSuperExpression expression) {
    PsiJavaCodeReferenceElement ref = expression.getQualifier();
    if (ref != null) {
      PsiElement element = ref.resolve();
      if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass) element;
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
