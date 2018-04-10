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
package com.intellij.psi.search;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A general interface to perform PsiElement's search scope optimization. The interface should be used only for optimization purposes.
 * It's used in:
 *
 * 1. {@link PsiSearchHelper#getUseScope(PsiElement)},
 *    {@link PsiSearchHelperImpl#USE_SCOPE_OPTIMIZER_EP_NAME}
 * to perform optimization of PsiElement's use scope.
 *
 * 2. {@link SearchRequestCollector#searchWord(String, SearchScope, short, boolean, String, RequestResultProcessor, PsiElement)},
 *    {@link SearchRequestCollector#CODE_USAGE_SCOPE_OPTIMIZER_EP_NAME}
 * to exclude a scope without references in code from a usages search when the search with {@link UsageSearchContext#IN_CODE} or {@link UsageSearchContext#ANY}
 *  context was requested.
 *
 */
public interface ScopeOptimizer {

  /**
   * Please use {@link ScopeOptimizer#getRestrictedUseScope(PsiElement)} instead
   */
  @Deprecated
  @Nullable("is null when given optimizer can't provide a scope to exclude")
  default GlobalSearchScope getScopeToExclude(@NotNull PsiElement element) {
    return null;
  }

  @Nullable("is null when given optimizer can't provide a scope to restrict")
  default SearchScope getRestrictedUseScope(@NotNull PsiElement element) {
    GlobalSearchScope scopeToExclude = getScopeToExclude(element);

    return scopeToExclude == null ? null : GlobalSearchScope.notScope(scopeToExclude);
  }

  @Nullable
  static SearchScope calculateOverallRestrictedUseScope(@NotNull ScopeOptimizer[] optimizers, @NotNull PsiElement element) {
    return Stream
      .of(optimizers)
      .peek(optimizer -> ProgressManager.checkCanceled())
      .map(optimizer -> optimizer.getRestrictedUseScope(element))
      .filter(Objects::nonNull)
      .reduce((s1, s2) -> s1.intersectWith(s2))
      .orElse(null);
  }
}
