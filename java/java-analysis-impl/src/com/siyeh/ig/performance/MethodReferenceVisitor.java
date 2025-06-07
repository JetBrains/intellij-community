/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MethodReferenceVisitor extends JavaRecursiveElementWalkingVisitor {
  private boolean m_referencesStaticallyAccessible = true;
  private final PsiMember m_method;

  public MethodReferenceVisitor(PsiMember method) {
    m_method = method;
  }

  public boolean areReferencesStaticallyAccessible() {
    return m_referencesStaticallyAccessible;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!m_referencesStaticallyAccessible) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);
    final PsiClass aClass = ObjectUtils.tryCast(reference.resolve(), PsiClass.class);
    if (aClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC) && aClass.getScope() instanceof PsiClass) {
      m_referencesStaticallyAccessible = false;
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    final PsiElement qualifier = expression.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiQualifiedExpression) {
      final PsiElement element = expression.resolve();
      if (element != null) {
        if ((element instanceof PsiLocalVariable || element instanceof PsiParameter) && 
            PsiTreeUtil.isAncestor(PsiUtil.getVariableCodeBlock((PsiVariable)element, null), m_method, true)) {
          m_referencesStaticallyAccessible = false;
          return;
        }
        if (!(element instanceof PsiMember member)) return;
        if (member == m_method || member.hasModifierProperty(PsiModifier.STATIC) ||
            member instanceof PsiClass && member.getContainingClass() == null) {
          return;
        }
        PsiExpression effectiveQualifier = ExpressionUtils.getEffectiveQualifier(expression);
        if (effectiveQualifier != null) {
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(effectiveQualifier.getType());
          if (aClass != null && PsiTreeUtil.isAncestor(m_method, aClass, false)) return;
        }
      }
      m_referencesStaticallyAccessible = false;
    }
  }

  @Override
  public void visitThisExpression(@NotNull PsiThisExpression expression) {
    super.visitThisExpression(expression);
    m_referencesStaticallyAccessible = false;
  }
}
