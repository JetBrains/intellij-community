/*
 * Copyright 2006-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ConcurrencyAnnotationsManager;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.AddToInspectionOptionListFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class AccessToStaticFieldLockedOnInstanceInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public OrderedSet<String> ignoredClasses = new OrderedSet<>();

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(stringList(
      "ignoredClasses", InspectionGadgetsBundle.message("options.label.ignored.classes"), 
      new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.class.type.to.ignore"))
    ));
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (aClass == null) return null;
    final String name = aClass.getQualifiedName();
    if (name == null) return null;
    return new AddToInspectionOptionListFix<>(this,
                                              InspectionGadgetsBundle.message("access.to.static.field.locked.on.instance.fix.name", name),
                                              name,
                                              tool -> tool.ignoredClasses);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("access.to.static.field.locked.on.instance.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AccessToStaticFieldLockedOnInstanceVisitor();
  }

  private class AccessToStaticFieldLockedOnInstanceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField lockedField)) {
        return;
      }
      if (!lockedField.hasModifierProperty(PsiModifier.STATIC) || ExpressionUtils.isConstant(lockedField)) {
        return;
      }
      if (lockedField.hasModifierProperty(PsiModifier.FINAL)) {
        final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(lockedField.getType());
        final ConcurrencyAnnotationsManager annotationsManager = ConcurrencyAnnotationsManager.getInstance(expression.getProject());
        final List<String> list = annotationsManager.getThreadSafeList();
        if (AnnotationUtil.findAnnotation(aClass, list) != null) {
          return;
        }
      }
      final PsiClass containingClass = lockedField.getContainingClass();
      if (!PsiTreeUtil.isAncestor(containingClass, expression, false)) {
        return;
      }
      if (!ignoredClasses.isEmpty() && ignoredClasses.contains(TypeUtils.resolvedClassName(lockedField.getType()))) {
        return;
      }

      boolean isLockedOnInstance = false;
      boolean isLockedOnClass = false;
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        if (containingMethod.hasModifierProperty(PsiModifier.STATIC)) {
          isLockedOnClass = true;
        }
        else {
          isLockedOnInstance = true;
        }
      }
      final PsiClass expressionClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (expressionClass == null) {
        return;
      }
      PsiElement elementToCheck = expression;
      while (true) {
        final PsiSynchronizedStatement synchronizedStatement = PsiTreeUtil.getParentOfType(elementToCheck, PsiSynchronizedStatement.class);
        if (synchronizedStatement == null || !PsiTreeUtil.isAncestor(expressionClass, synchronizedStatement, true)) {
          break;
        }
        final PsiExpression lockExpression = synchronizedStatement.getLockExpression();
        if (lockExpression instanceof PsiReferenceExpression reference) {
          final PsiElement lockTarget = reference.resolve();
          if (lockTarget instanceof PsiField lockField) {
            if (lockField.hasModifierProperty(PsiModifier.STATIC)) {
              isLockedOnClass = true;
            }
            else {
              isLockedOnInstance = true;
            }
          }
        }
        else if (lockExpression instanceof PsiThisExpression) {
          isLockedOnInstance = true;
        }
        else if (lockExpression instanceof PsiClassObjectAccessExpression) {
          isLockedOnClass = true;
        }
        elementToCheck = synchronizedStatement;
      }
      if (!isLockedOnInstance || isLockedOnClass) {
        return;
      }
      registerError(expression, expression);
    }
  }
}