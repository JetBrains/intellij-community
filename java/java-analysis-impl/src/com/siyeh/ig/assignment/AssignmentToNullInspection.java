/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.assignment;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class AssignmentToNullInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAssignmentsToFields = false;

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.null.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final Object info = infos[0];
    if (!(info instanceof PsiReferenceExpression referenceExpression)) {
      return null;
    }
    if (TypeUtils.isOptional(referenceExpression.getType())) {
      return null;
    }
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable variable)) {
      return null;
    }
    if (NullableNotNullManager.isNotNull(variable)) {
      return null;
    }
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(target.getProject());
    String annotation = manager.getDefaultAnnotation(Nullability.NULLABLE, variable);
    if (JavaPsiFacade.getInstance(variable.getProject()).findClass(annotation, variable.getResolveScope()) == null) {
      return null;
    }
    return LocalQuickFix.from(new AddAnnotationModCommandAction(annotation, variable));
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAssignmentsToFields", InspectionGadgetsBundle.message(
        "assignment.to.null.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToNullVisitor();
  }

  private class AssignmentToNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @NotNull PsiLiteralExpression value) {
      super.visitLiteralExpression(value);
      final String text = value.getText();
      if (!JavaKeywords.NULL.equals(text)) {
        return;
      }
      PsiElement parent = value.getParent();
      while (parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression ||
             parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (lhs == null || isReferenceToNullableVariable(lhs)) {
        return;
      }
      registerError(lhs, lhs);
    }

    private boolean isReferenceToNullableVariable(
      PsiExpression lhs) {
      if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiVariable variable)) {
        return false;
      }
      if (ignoreAssignmentsToFields && variable instanceof PsiField) {
        return true;
      }
      return NullableNotNullManager.isNullable(variable);
    }
  }
}