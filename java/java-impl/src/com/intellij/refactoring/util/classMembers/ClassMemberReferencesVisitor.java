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

public abstract class ClassMemberReferencesVisitor extends JavaRecursiveElementWalkingVisitor {
  private final PsiClass myClass;

  public ClassMemberReferencesVisitor(PsiClass aClass) {
    myClass = aClass;
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
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

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    PsiElement referencedElement = reference.resolve();
    if (referencedElement instanceof PsiClass) {
      final PsiClass referencedClass = (PsiClass) referencedElement;
      if (PsiTreeUtil.isAncestor(myClass, referencedElement, true)) {
        visitClassMemberReferenceElement((PsiMember)referencedElement, reference);
      }
      else if (isPartOf (myClass, referencedClass.getContainingClass()))
      {
        visitClassMemberReferenceElement((PsiMember)referencedElement, reference);
      }
    }
  }

  protected final PsiClass getPsiClass() {
    return myClass;
  }
}
