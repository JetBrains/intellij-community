// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A general interface to perform PsiElement's search scope optimization. The interface should be used only for optimization purposes.
 * It's used in:
 * <ol>
 * <li>
 * {@link PsiSearchHelper#getUseScope(PsiElement)},
 * {@link com.intellij.psi.impl.search.PsiSearchHelperImpl#USE_SCOPE_OPTIMIZER_EP_NAME}
 * to perform optimization of PsiElement's use scope.
 * </li>
 * <li>
 * {@link SearchRequestCollector#searchWord(String, SearchScope, short, boolean, PsiElement)},
 * {@link PsiSearchHelper#getCodeUsageScope(PsiElement)},
 * {@link PsiSearchHelper#CODE_USAGE_SCOPE_OPTIMIZER_EP_NAME}
 * to exclude a scope without references in code from a usages search when the search with {@link UsageSearchContext#IN_CODE} or {@link UsageSearchContext#ANY}
 * context was requested.
 * </li>
 * </ol>
 */
public interface ScopeOptimizer {

  /**
   * @deprecated use {@link ScopeOptimizer#getRestrictedUseScope(PsiElement)} instead.
   */
  @Deprecated
  default @Nullable("is null when given optimizer can't provide a scope to exclude") GlobalSearchScope getScopeToExclude(@NotNull PsiElement element) {
    return null;
  }

  default @Nullable("is null when given optimizer can't provide a scope to restrict") SearchScope getRestrictedUseScope(@NotNull PsiElement element) {
    GlobalSearchScope scopeToExclude = getScopeToExclude(element);

    return scopeToExclude == null ? null : GlobalSearchScope.notScope(scopeToExclude);
  }

  static @Nullable SearchScope calculateOverallRestrictedUseScope(@NotNull List<? extends ScopeOptimizer> optimizers, @NotNull PsiElement element) {
    boolean seen = false;
    SearchScope acc = null;
    for (ScopeOptimizer optimizer : optimizers) {
      ProgressManager.checkCanceled();
      SearchScope scope = optimizer.getRestrictedUseScope(element);
      if (scope != null) {
        if (!seen) {
          seen = true;
          acc = scope;
        }
        else {
          acc = acc.intersectWith(scope);
        }
      }
    }
    return seen ? acc : null;
  }
}
