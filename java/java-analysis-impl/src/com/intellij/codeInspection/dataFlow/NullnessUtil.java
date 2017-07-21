/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.extensions.Extensions;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NullnessUtil {

  static Boolean calcCanBeNull(DfaVariableValue value) {
    PsiModifierListOwner var = value.getPsiVariable();
    Nullness nullability = DfaPsiUtil.getElementNullability(value.getVariableType(), var);
    if (nullability != Nullness.UNKNOWN) {
      return toBoolean(nullability);
    }

    Nullness defaultNullability = value.getFactory().suggestNullabilityForNonAnnotatedMember(var);

    if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)var.getParent()).getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return toBoolean(DfaPsiUtil.getElementNullability(itemType, var));
        }
      }
    }

    if (var instanceof PsiField && value.getFactory().isHonorFieldInitializers()) {
      return toBoolean(getNullabilityFromFieldInitializers((PsiField)var, defaultNullability));
    }

    return toBoolean(defaultNullability);
  }

  private static Nullness getNullabilityFromFieldInitializers(PsiField field, Nullness defaultNullability) {
    if (DfaPsiUtil.isFinalField(field)) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null) {
        return getExpressionNullness(initializer);
      }

      List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
      if (initializers.isEmpty()) {
        return defaultNullability;
      }

      for (PsiExpression expression : initializers) {
        if (getExpressionNullness(expression) == Nullness.NULLABLE) {
          return Nullness.NULLABLE;
        }
      }

      if (DfaPsiUtil.isInitializedNotNull(field)) {
        return Nullness.NOT_NULL;
      }
    }
    else if (isOnlyImplicitlyInitialized(field)) {
      return Nullness.NOT_NULL;
    }
    return defaultNullability;
  }

  private static boolean isOnlyImplicitlyInitialized(PsiField field) {
    return CachedValuesManager.getCachedValue(field, () -> CachedValueProvider.Result.create(
      isImplicitlyInitializedNotNull(field) && weAreSureThereAreNoExplicitWrites(field),
      PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean isImplicitlyInitializedNotNull(PsiField field) {
    return ContainerUtil.exists(Extensions.getExtensions(ImplicitUsageProvider.EP_NAME), p -> p.isImplicitlyNotNullInitialized(field));
  }

  private static boolean weAreSureThereAreNoExplicitWrites(PsiField field) {
    String name = field.getName();
    if (name == null || field.getInitializer() != null) return false;

    if (!isCheapEnoughToSearch(field, name)) return false;

    return ReferencesSearch
      .search(field).forEach(
        reference -> reference instanceof PsiReferenceExpression && !PsiUtil.isAccessedForWriting((PsiReferenceExpression)reference));
  }

  private static boolean isCheapEnoughToSearch(PsiField field, String name) {
    SearchScope scope = field.getUseScope();
    if (!(scope instanceof GlobalSearchScope)) return true;

    PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(field.getProject());
    PsiSearchHelper.SearchCostResult result =
      helper.isCheapEnoughToSearch(name, (GlobalSearchScope)scope, field.getContainingFile(), null);
    return result != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
  }

  public static Nullness getExpressionNullness(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return Nullness.UNKNOWN;
    if (expression.textMatches(PsiKeyword.NULL)) return Nullness.NULLABLE;
    if (expression instanceof PsiNewExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiPolyadicExpression ||
        expression instanceof PsiFunctionalExpression ||
        expression.getType() instanceof PsiPrimitiveType) {
      return Nullness.NOT_NULL;
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      if (thenExpression == null || elseExpression == null) return Nullness.UNKNOWN;
      Nullness left = getExpressionNullness(thenExpression);
      if (left == Nullness.UNKNOWN) return Nullness.UNKNOWN;
      Nullness right = getExpressionNullness(elseExpression);
      return left == right ? left : Nullness.UNKNOWN;
    }
    if (expression instanceof PsiTypeCastExpression) {
      return getExpressionNullness(((PsiTypeCastExpression)expression).getOperand());
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)expression).resolve();
      return DfaPsiUtil.getElementNullability(expression.getType(), (PsiModifierListOwner)target);
    }
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        return getExpressionNullness(assignment.getRExpression());
      }
      return Nullness.NOT_NULL;
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
      return method != null ? DfaPsiUtil.getElementNullability(expression.getType(), method) : Nullness.UNKNOWN;
    }
    return Nullness.UNKNOWN;
  }

  /**
   * Convert from boolean fact which is used to encode nullability in DfaFactType.CAN_BE_NULL
   *
   * @param fact TRUE if NULLABLE, FALSE if NOT_NULL, null if UNKNOWN
   * @return the corresponding nullness value
   */
  @NotNull
  public static Nullness fromBoolean(@Nullable Boolean fact) {
    return fact == null ? Nullness.UNKNOWN : fact ? Nullness.NULLABLE : Nullness.NOT_NULL;
  }

  /**
   * Convert nullness to boolean which is used to encode nullability in DfaFactType.CAN_BE_NULL
   *
   * @return TRUE if NULLABLE, FALSE if NOT_NULL, null if UNKNOWN
   */
  @Nullable
  public static Boolean toBoolean(@NotNull Nullness nullness) {
    return nullness == Nullness.UNKNOWN ? null : nullness == Nullness.NULLABLE;
  }
}
