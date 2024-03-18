/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryUnboxingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportSuperfluouslyUnboxed = false;

  @NonNls static final Map<String, String> s_unboxingMethods = new HashMap<>(8);

  static {
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_INTEGER, "intValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_SHORT, "shortValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_BOOLEAN, "booleanValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_LONG, "longValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_BYTE, "byteValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_FLOAT, "floatValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_DOUBLE, "doubleValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_CHARACTER, "charValue");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.unboxing.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyReportSuperfluouslyUnboxed", InspectionGadgetsBundle.message("unnecessary.unboxing.superfluous.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryUnboxingFix();
  }

  private static class UnnecessaryUnboxingFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.unboxing.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement grandParent = element.getParent().getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCall)) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiExpression strippedQualifier = PsiUtil.skipParenthesizedExprDown(qualifier);
      if (strippedQualifier == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      if (strippedQualifier instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiField field) {
          final PsiClass containingClass = field.getContainingClass();
          if (containingClass == null) {
            return;
          }
          final String classname = containingClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(classname)) {
            @NonNls final String name = field.getName();
            if ("TRUE".equals(name)) {
              PsiReplacementUtil.replaceExpression(methodCall, "true", commentTracker);
              return;
            }
            else if ("FALSE".equals(name)) {
              PsiReplacementUtil.replaceExpression(methodCall, "false", commentTracker);
              return;
            }
          }
        }
      }
      final String strippedQualifierText = commentTracker.text(strippedQualifier);
      PsiReplacementUtil.replaceExpression(methodCall, strippedQualifierText, commentTracker);
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnboxingVisitor();
  }

  private class UnnecessaryUnboxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isUnboxingExpression(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null || isUnboxingNecessary(expression, qualifier)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private boolean isUnboxingNecessary(@NotNull PsiExpression expression, @NotNull PsiExpression unboxedExpression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        expression = (PsiExpression)parent;
        parent = parent.getParent();
      }
      if (parent instanceof PsiPolyadicExpression polyadicExpression) {
        if (isPossibleObjectComparison(expression, polyadicExpression)) {
          return true;
        }
      }
      if (parent instanceof PsiTypeCastExpression typeCastExpression) {
        final PsiTypeElement typeElement = typeCastExpression.getCastType();
        if (typeElement == null) {
          return true;
        }
        final PsiType castType = typeElement.getType();
        final PsiType expressionType = expression.getType();
        if (expressionType == null || !castType.isAssignableFrom(expressionType)) {
          return true;
        }
      }
      else if (parent instanceof PsiConditionalExpression conditionalExpression) {
        final PsiExpression thenExpression = conditionalExpression.getThenExpression();
        if (thenExpression == null) {
          return true;
        }
        final PsiExpression elseExpression = conditionalExpression.getElseExpression();
        if (elseExpression == null) {
          return true;
        }
        if (PsiTreeUtil.isAncestor(thenExpression, expression, false)) {
          final PsiType type = elseExpression.getType();
          if (!(type instanceof PsiPrimitiveType)) {
            return true;
          }
        }
        else if (PsiTreeUtil.isAncestor(elseExpression, expression, false)) {
          final PsiType type = thenExpression.getType();
          if (!(type instanceof PsiPrimitiveType)) {
            return true;
          }
        }
      }
      else if (MethodCallUtils.isNecessaryForSurroundingMethodCall(expression, unboxedExpression)) {
        return true;
      }

      if (!LambdaUtil.isSafeLambdaReturnValueReplacement(expression, unboxedExpression)) return true;

      if (onlyReportSuperfluouslyUnboxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiClassType)) {
          return true;
        }
      }
      return false;
    }

    private boolean isPossibleObjectComparison(PsiExpression expression, PsiPolyadicExpression polyadicExpression) {
      if (!ComparisonUtils.isEqualityComparison(polyadicExpression)) {
        return false;
      }
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (operand == expression) {
          continue;
        }
        if (!(operand.getType() instanceof PsiPrimitiveType) || isUnboxingExpression(operand)) {
          return true;
        }
      }
      return false;
    }

    private static boolean isUnboxingExpression(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      final PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) {
        return false;
      }
      final String qualifierTypeName = qualifierType.getCanonicalText();
      if (!s_unboxingMethods.containsKey(qualifierTypeName)) {
        return false;
      }
      final String methodName = methodExpression.getReferenceName();
      final String unboxingMethod = s_unboxingMethods.get(qualifierTypeName);
      return unboxingMethod.equals(methodName);
    }
  }
}