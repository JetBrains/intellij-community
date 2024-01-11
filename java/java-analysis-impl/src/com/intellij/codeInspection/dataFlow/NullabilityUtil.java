// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class NullabilityUtil {

  public static Pair<PsiExpression, Nullability> getNullabilityFromFieldInitializers(PsiField field) {
    if (DfaPsiUtil.isFinalField(field) && PsiAugmentProvider.canTrustFieldInitializer(field)) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null) {
        return Pair.create(initializer, getExpressionNullability(initializer));
      }

      List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
      if (initializers.isEmpty()) {
        return Pair.create(null, Nullability.UNKNOWN);
      }

      for (PsiExpression expression : initializers) {
        Nullability nullability = getExpressionNullability(expression);
        if (nullability == Nullability.NULLABLE) {
          return Pair.create(expression, Nullability.NULLABLE);
        }
      }

      if (DfaPsiUtil.isInitializedNotNull(field)) {
        return Pair.create(ContainerUtil.getOnlyItem(initializers), Nullability.NOT_NULL);
      }
    }
    else if (isOnlyImplicitlyInitialized(field)) {
      return Pair.create(null, Nullability.NOT_NULL);
    }
    return Pair.create(null, Nullability.UNKNOWN);
  }

  private static boolean isOnlyImplicitlyInitialized(PsiField field) {
    return CachedValuesManager.getCachedValue(field, () -> CachedValueProvider.Result.create(
      isImplicitlyInitializedNotNull(field) && weAreSureThereAreNoExplicitWrites(field),
      PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean isImplicitlyInitializedNotNull(PsiField field) {
    return ContainerUtil.exists(ImplicitUsageProvider.EP_NAME.getExtensionList(), p -> p.isImplicitlyNotNullInitialized(field));
  }

  private static boolean weAreSureThereAreNoExplicitWrites(PsiField field) {
    if (field.hasInitializer()) return false;
    if (!field.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    return !VariableAccessUtils.variableIsAssigned(field);
  }

  public static Nullability getExpressionNullability(@Nullable PsiExpression expression) {
    return getExpressionNullability(expression, false);
  }

  /**
   * Tries to determine an expression nullability
   *
   * @param expression an expression to check
   * @param useDataflow whether to use dataflow (more expensive, but may produce more precise result)
   * @return expression nullability. UNKNOWN if unable to determine;
   * NULLABLE if known to possibly have null value; NOT_NULL if definitely never null.
   */
  public static Nullability getExpressionNullability(@Nullable PsiExpression expression, boolean useDataflow) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return Nullability.UNKNOWN;
    if (PsiTypes.nullType().equals(expression.getType())) return Nullability.NULLABLE;
    if (expression instanceof PsiNewExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiPolyadicExpression ||
        expression instanceof PsiFunctionalExpression ||
        expression.getType() instanceof PsiPrimitiveType) {
      return Nullability.NOT_NULL;
    }
    boolean dumb = DumbService.isDumb(expression.getProject());
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      if (thenExpression == null || elseExpression == null) return Nullability.UNKNOWN;
      PsiExpression condition = ((PsiConditionalExpression)expression).getCondition();
      // simple cases like x == null ? something : x
      PsiReferenceExpression ref = ExpressionUtils.getReferenceExpressionFromNullComparison(condition, true);
      if (ref != null && JavaPsiEquivalenceUtil.areExpressionsEquivalent(ref, elseExpression)) {
        return getExpressionNullability(thenExpression, useDataflow);
      }
      // x != null ? x : something
      ref = ExpressionUtils.getReferenceExpressionFromNullComparison(condition, false);
      if (ref != null && JavaPsiEquivalenceUtil.areExpressionsEquivalent(ref, thenExpression)) {
        return getExpressionNullability(elseExpression, useDataflow);
      }
      if (useDataflow && !dumb) {
        return DfaNullability.toNullability(DfaNullability.fromDfType(CommonDataflow.getDfType(expression)));
      }
      Nullability left = getExpressionNullability(thenExpression, false);
      if (left == Nullability.UNKNOWN) return Nullability.UNKNOWN;
      Nullability right = getExpressionNullability(elseExpression, false);
      return left == right ? left : Nullability.UNKNOWN;
    }
    if (expression instanceof PsiTypeCastExpression) {
      return getExpressionNullability(((PsiTypeCastExpression)expression).getOperand(), useDataflow);
    }
    if (expression instanceof PsiAssignmentExpression assignment) {
      if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        return getExpressionNullability(assignment.getRExpression(), useDataflow);
      }
      return Nullability.NOT_NULL;
    }
    if (useDataflow && !dumb) {
      return DfaNullability.toNullability(DfaNullability.fromDfType(CommonDataflow.getDfType(expression)));
    }
    if (expression instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      if (target instanceof PsiPatternVariable) {
        return Nullability.NOT_NULL; // currently all pattern variables are not-null
      }
      if (dumb) return Nullability.UNKNOWN;
      if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
        PsiElement block = PsiUtil.getVariableCodeBlock((PsiVariable)target, null);
        // Do not trust the declared nullability of local variable/parameter if it's reassigned as nullability designates
        // only initial nullability
        if (block == null || !HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)target, block, ref)) return Nullability.UNKNOWN;
      }
      return DfaPsiUtil.getElementNullabilityIgnoringParameterInference(expression.getType(), (PsiModifierListOwner)target);
    }
    if (expression instanceof PsiMethodCallExpression || expression instanceof PsiTemplateExpression) {
      if (dumb) return Nullability.UNKNOWN;
      PsiMethod method = ((PsiCall)expression).resolveMethod();
      return method != null ? DfaPsiUtil.getElementNullability(expression.getType(), method) : Nullability.UNKNOWN;
    }
    return Nullability.UNKNOWN;
  }
}
