// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.value.DfaExpressionFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NullabilityUtil {

  static DfaNullability calcCanBeNull(DfaVariableValue value) {
    if (value.getDescriptor() instanceof DfaExpressionFactory.ThisDescriptor) {
      return DfaNullability.NOT_NULL;
    }
    if (value.getDescriptor() == SpecialField.OPTIONAL_VALUE) {
      return DfaNullability.NULLABLE;
    }
    PsiModifierListOwner var = value.getPsiVariable();
    if (value.getType() instanceof PsiPrimitiveType) {
      return null;
    }
    Nullability nullability = DfaPsiUtil.getElementNullabilityIgnoringParameterInference(value.getType(), var);
    if (nullability != Nullability.UNKNOWN) {
      return DfaNullability.fromNullability(nullability);
    }
    if (var == null) return null;

    Nullability defaultNullability = value.getFactory().suggestNullabilityForNonAnnotatedMember(var);

    if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)var.getParent()).getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return DfaNullability.fromNullability(DfaPsiUtil.getElementNullability(itemType, var));
        }
      }
    }

    if (var instanceof PsiField && value.getFactory().canTrustFieldInitializer((PsiField)var)) {
      return DfaNullability.fromNullability(getNullabilityFromFieldInitializers((PsiField)var, defaultNullability));
    }

    return DfaNullability.fromNullability(defaultNullability);
  }

  private static Nullability getNullabilityFromFieldInitializers(PsiField field, Nullability defaultNullability) {
    if (DfaPsiUtil.isFinalField(field)) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null) {
        return getExpressionNullability(initializer);
      }

      List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
      if (initializers.isEmpty()) {
        return defaultNullability;
      }

      for (PsiExpression expression : initializers) {
        if (getExpressionNullability(expression) == Nullability.NULLABLE) {
          return Nullability.NULLABLE;
        }
      }

      if (DfaPsiUtil.isInitializedNotNull(field)) {
        return Nullability.NOT_NULL;
      }
    }
    else if (isOnlyImplicitlyInitialized(field)) {
      return Nullability.NOT_NULL;
    }
    return defaultNullability;
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
    String name = field.getName();
    if (name == null || field.getInitializer() != null) return false;

    if (!isCheapEnoughToSearch(field, name)) return false;

    return ReferencesSearch.search(field).allMatch(
        reference -> reference instanceof PsiReferenceExpression && !PsiUtil.isAccessedForWriting((PsiReferenceExpression)reference));
  }

  private static boolean isCheapEnoughToSearch(PsiField field, String name) {
    SearchScope scope = field.getUseScope();
    if (!(scope instanceof GlobalSearchScope)) return true;

    PsiSearchHelper helper = PsiSearchHelper.getInstance(field.getProject());
    PsiSearchHelper.SearchCostResult result =
      helper.isCheapEnoughToSearch(name, (GlobalSearchScope)scope, field.getContainingFile(), null);
    return result != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
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
    if (expression.textMatches(PsiKeyword.NULL)) return Nullability.NULLABLE;
    if (expression instanceof PsiNewExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiPolyadicExpression ||
        expression instanceof PsiFunctionalExpression ||
        expression.getType() instanceof PsiPrimitiveType) {
      return Nullability.NOT_NULL;
    }
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
      if (useDataflow) {
        return DfaNullability.toNullability(CommonDataflow.getExpressionFact(expression, DfaFactType.NULLABILITY));
      }
      Nullability left = getExpressionNullability(thenExpression, false);
      if (left == Nullability.UNKNOWN) return Nullability.UNKNOWN;
      Nullability right = getExpressionNullability(elseExpression, false);
      return left == right ? left : Nullability.UNKNOWN;
    }
    if (expression instanceof PsiTypeCastExpression) {
      return getExpressionNullability(((PsiTypeCastExpression)expression).getOperand(), useDataflow);
    }
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        return getExpressionNullability(assignment.getRExpression(), useDataflow);
      }
      return Nullability.NOT_NULL;
    }
    if (useDataflow) {
      return DfaNullability.toNullability(CommonDataflow.getExpressionFact(expression, DfaFactType.NULLABILITY));
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)expression;
      PsiElement target = (ref).resolve();
      if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
        PsiElement block = PsiUtil.getVariableCodeBlock((PsiVariable)target, null);
        // Do not trust the declared nullability of local variable/parameter if it's reassigned as nullability designates
        // only initial nullability
        if (block == null || !HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)target, block, ref)) return Nullability.UNKNOWN;
      }
      return DfaPsiUtil.getElementNullabilityIgnoringParameterInference(expression.getType(), (PsiModifierListOwner)target);
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
      return method != null ? DfaPsiUtil.getElementNullability(expression.getType(), method) : Nullability.UNKNOWN;
    }
    return Nullability.UNKNOWN;
  }
}
