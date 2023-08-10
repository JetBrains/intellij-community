/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.memory;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class InnerClassReferenceVisitor extends JavaRecursiveElementWalkingVisitor {

  private final PsiClass innerClass;
  private boolean referencesStaticallyAccessible = true;

  public InnerClassReferenceVisitor(@NotNull PsiClass innerClass) {
    this.innerClass = innerClass;
  }

  public boolean canInnerClassBeStatic() {
    final PsiClass superClass = innerClass.getSuperClass();
    if (superClass != null && !isClassStaticallyAccessible(superClass)) {
      return false;
    }
    return referencesStaticallyAccessible;
  }

  private boolean isClassStaticallyAccessible(@NotNull PsiClass aClass) {
    if (PsiTreeUtil.isAncestor(innerClass, aClass, false) || aClass.hasModifierProperty(PsiModifier.STATIC)) {
      return true;
    }
    if (aClass instanceof PsiTypeParameter) {
      PsiTypeParameterListOwner owner = ((PsiTypeParameter)aClass).getOwner();
      return owner != null && PsiTreeUtil.isAncestor(innerClass, owner, false);
    }
    final PsiClass containingClass = aClass.getContainingClass();
    return containingClass == null || InheritanceUtil.isInheritorOrSelf(innerClass, containingClass, true);
  }

  @Override
  public void visitThisExpression(@NotNull PsiThisExpression expression) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    super.visitThisExpression(expression);
    if (hasContainingClassQualifier(expression)) {
      referencesStaticallyAccessible = false;
    }
  }

  @Override
  public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    super.visitSuperExpression(expression);
    if (hasContainingClassQualifier(expression)) {
      referencesStaticallyAccessible = false;
    }
  }

  private boolean hasContainingClassQualifier(PsiQualifiedExpression expression) {
    final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
    return qualifier != null && !innerClass.equals(qualifier.resolve());
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement expression) {
    if (!referencesStaticallyAccessible) {
      return;
    }

    super.visitReferenceElement(expression);
    if (expression.isQualified()) {
      return;
    }
    final PsiElement target = expression.resolve();
    if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
      return;
    }
    if (target instanceof PsiMethod || target instanceof PsiField) {
      final PsiMember member = (PsiMember)target;
      if (member.hasModifierProperty(PsiModifier.STATIC) || PsiTreeUtil.isAncestor(innerClass, member, true)) {
        return;
      }
      if (!member.hasModifierProperty(PsiModifier.PRIVATE)) {
        final PsiClass containingClass = member.getContainingClass();
        if (InheritanceUtil.isInheritorOrSelf(innerClass, containingClass, true)) {
          return;
        }

        PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        while (parentClass != null && PsiTreeUtil.isAncestor(innerClass, parentClass, true)) {
          if (InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
            return;
          }
          parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
        }
      }
      referencesStaticallyAccessible = false;
    }
    else if (target instanceof PsiClass && !isClassStaticallyAccessible((PsiClass)target)) {
      referencesStaticallyAccessible = false;
    }
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    super.visitNewExpression(expression);
    final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference == null) {
      return;
    }
    final PsiElement target = classReference.resolve();
    if (!(target instanceof PsiClass)) {
      return;
    }
    if (!isClassStaticallyAccessible((PsiClass)target)) {
      referencesStaticallyAccessible = false;
    }
  }

  @Override
  public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    super.visitTypeElement(typeElement);
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
    if (aClass instanceof PsiTypeParameter && !PsiTreeUtil.isAncestor(innerClass, aClass, true)) {
      referencesStaticallyAccessible = false;
    }
  }
}
