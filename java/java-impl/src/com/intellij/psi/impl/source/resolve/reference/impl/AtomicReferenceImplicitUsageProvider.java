// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;
import static com.intellij.psi.search.PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES;
import static com.intellij.psi.util.PsiUtil.skipParenthesizedExprDown;
import static com.intellij.psi.util.PsiUtil.skipParenthesizedExprUp;

public final class AtomicReferenceImplicitUsageProvider implements ImplicitUsageProvider {
  private static final Set<String> ourUpdateMethods = Set.of(
    "compareAndSet", "weakCompareAndSet", "set", "lazySet", "getAndSet", "getAndIncrement", "getAndDecrement", "getAndAdd",
    "incrementAndGet", "decrementAndGet", "addAndGet", "getAndUpdate", "updateAndGet", "getAndAccumulate", "accumulateAndGet");

  @Override
  public boolean isImplicitUsage(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitRead(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(@NotNull PsiElement element) {
    if (element instanceof PsiField field && field.hasModifierProperty(PsiModifier.VOLATILE)) {
      return CachedValuesManager.getCachedValue(field, () ->
        new CachedValueProvider.Result<>(isAtomicWrite(field), PsiModificationTracker.MODIFICATION_COUNT));
    }
    return false;
  }

  private static boolean isAtomicWrite(@NotNull PsiField field) {
    PsiType type = field.getType();
    if (PsiTypes.intType().equals(type)) {
      return isAtomicWrite(field, ATOMIC_INTEGER_FIELD_UPDATER);
    }
    if (PsiTypes.longType().equals(type)) {
      return isAtomicWrite(field, ATOMIC_LONG_FIELD_UPDATER);
    }
    if (!(type instanceof PsiPrimitiveType)) {
      return isAtomicWrite(field, ATOMIC_REFERENCE_FIELD_UPDATER);
    }
    return false;
  }

  private static boolean isAtomicWrite(@NotNull PsiField field, @NonNls String updaterName) {
    SearchScope scope = getCheapSearchScope(field);
    if (scope == null) {
      return false;
    }
    Query<PsiReference> fieldQuery = ReferencesSearch.search(field, scope);
    return !fieldQuery.forEach((PsiReference reference) -> findAtomicUpdaters(reference, updaterName));
  }

  private static boolean findAtomicUpdaters(@NotNull PsiReference reference, @NotNull String updaterName) {
    if (!(reference instanceof JavaLangClassMemberReference)) { // optimization
      return true;
    }
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethodCallExpression.class);
    if (methodCall == null || !isCallToMethod(methodCall, updaterName, NEW_UPDATER)) {
      return true;
    }
    PsiElement callParent = skipParenthesizedExprUp(methodCall.getParent());
    PsiVariable updaterVariable = null;
    if (callParent instanceof PsiVariable var && skipParenthesizedExprDown(var.getInitializer()) == methodCall) {
      updaterVariable = var;
    }
    else if (callParent instanceof PsiAssignmentExpression assignment) {
      if (assignment.getOperationTokenType() == JavaTokenType.EQ &&
          skipParenthesizedExprDown(assignment.getRExpression()) == methodCall &&
          skipParenthesizedExprDown(assignment.getLExpression()) instanceof PsiReferenceExpression refExpr &&
          refExpr.resolve() instanceof PsiVariable var) {
        updaterVariable = var;
      }
    }
    if (updaterVariable != null && InheritanceUtil.isInheritor(updaterVariable.getType(), updaterName)) {
      Query<PsiReference> updaterQuery = ReferencesSearch.search(updaterVariable);
      if (!updaterQuery.forEach(AtomicReferenceImplicitUsageProvider::findWrites)) {
        return false;
      }
    }
    return true;
  }

  private static boolean findWrites(@NotNull PsiReference reference) {
    PsiElement element = reference.getElement();
    PsiReferenceExpression methodExpression = ObjectUtils.tryCast(skipParenthesizedExprUp(element.getParent()), PsiReferenceExpression.class);
    if (methodExpression != null &&
        (methodExpression instanceof PsiMethodReferenceExpression || methodExpression.getParent() instanceof PsiMethodCallExpression) &&
        methodExpression.getReferenceName() != null &&
        ourUpdateMethods.contains(methodExpression.getReferenceName()) &&
        skipParenthesizedExprDown(methodExpression.getQualifierExpression()) == element) {

      return false;
    }
    return true;
  }

  @Nullable
  private static SearchScope getCheapSearchScope(@NotNull PsiField field) {
    SearchScope scope = field.getUseScope();
    if (scope instanceof LocalSearchScope) {
      return scope;
    }

    String name = field.getName();
    Project project = field.getProject();
    PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);

    if (scope instanceof GlobalSearchScope &&
        searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)scope, null, null) == FEW_OCCURRENCES) {
      return scope;
    }
    return null;
  }
}
