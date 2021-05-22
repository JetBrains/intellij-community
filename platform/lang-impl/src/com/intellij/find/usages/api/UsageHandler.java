// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api;

import com.intellij.util.Query;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
 * @param <O> type of search options.
 *            Nullability is omitted intentionally because implementations are responsible for it,
 *            e.g. if {@link #getCustomOptions} returns {@code null},
 *            then the same {@code null} is passed into {@link #buildSearchQuery} and {@link #getSearchString}.
 */
public interface UsageHandler<O> {

  enum UsageAction {
    FIND_USAGES,
    SHOW_USAGES,
    HIGHLIGHT_USAGES,
  }

  /**
   * Returned instance may be used to {@link #buildSearchQuery build search query},
   * or to initialize UI to obtain another instance of custom options configured by the user.
   * Custom options must have associated {@link com.intellij.openapi.options.OptionEditorProvider option editor provider}.
   *
   * @return instance of custom options or {@code null} if the handler doesn't have additional configuration
   */
  O getCustomOptions(@NotNull UsageAction action);

  /**
   * @return whether the combination of selected option might yield any results;
   * returning {@code false} from this method may prevent even starting the search
   */
  boolean hasAnythingToSearch(O customOptions);

  /**
   * @return search string to be shown in the Usage View and/or Usage Popup,
   * e.g. <i>Usages and Implementations of Method 'foo' of Class 'X'</i>
   */
  @Nls(capitalization = Title) @NotNull String getSearchString(@NotNull UsageOptions options, O customOptions);

  /**
   * @return query which will be executed on the background thread later additionally with {@link UsageSearchParameters} query
   */
  @NotNull Query<? extends @NotNull Usage> buildSearchQuery(@NotNull UsageOptions options, O customOptions);

  interface NonConfigurable {

    /**
     * @see UsageHandler#getSearchString
     */
    @Nls(capitalization = Title) @NotNull String getSearchString(@NotNull UsageOptions options);

    /**
     * @see UsageHandler#buildSearchQuery
     */
    @NotNull Query<? extends @NotNull Usage> buildSearchQuery(@NotNull UsageOptions options);
  }

  /**
   * @return a usage handler adapted from a handler without custom options
   */
  static @NotNull UsageHandler<?> createUsageHandler(@NotNull UsageHandler.NonConfigurable usageHandler) {
    return new NonConfigurableUsageHandler(usageHandler);
  }

  /**
   * @return a usage handler without custom options and without custom search query,
   * meaning the search is run only with {@link UsageSearchParameters}.
   */
  static @NotNull UsageHandler<?> createEmptyUsageHandler(@NotNull String targetName) {
    return createUsageHandler(new EmptyUsageHandler(targetName));
  }
}
