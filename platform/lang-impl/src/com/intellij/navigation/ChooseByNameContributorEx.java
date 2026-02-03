// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows a plugin to add items to "Navigate Class|File|Symbol" lists.
 *
 * @see GotoClassContributor
 * @see ChooseByNameRegistry
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/go-to-class-and-go-to-symbol.html">Go to Class and Go to Symbol</a>
 */
public interface ChooseByNameContributorEx extends ChooseByNameContributor {

  /**
   * Feeds the processor with all names available in the given scope.
   *
   * @param filter optional filter to use in an index used for searching names
   */
  void processNames(@NotNull Processor<? super String> processor,
                    @NotNull GlobalSearchScope scope,
                    @Nullable IdFilter filter);

  /**
   * Feeds the processor with {@link NavigationItem}s matching the given name and parameters.
   */
  void processElementsWithName(@NotNull String name,
                               @NotNull Processor<? super NavigationItem> processor,
                               @NotNull FindSymbolParameters parameters);

  /**
   * @deprecated Use {@link #processNames(Processor, GlobalSearchScope, IdFilter)} instead
   */
  @Deprecated
  @Override
  default String @NotNull [] getNames(Project project, boolean includeNonProjectItems) {
    List<String> result = new ArrayList<>();
    processNames(result::add, FindSymbolParameters.searchScopeFor(project, includeNonProjectItems), null);
    return ArrayUtilRt.toStringArray(result);
  }

  /**
   * @deprecated Use {@link #processElementsWithName(String, Processor, FindSymbolParameters)} instead
   */
  @Deprecated
  @Override
  default NavigationItem @NotNull [] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    List<NavigationItem> result = new ArrayList<>();
    processElementsWithName(name, result::add, FindSymbolParameters.simple(project, includeNonProjectItems));
    return result.isEmpty() ? NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY : result.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
  }
}
