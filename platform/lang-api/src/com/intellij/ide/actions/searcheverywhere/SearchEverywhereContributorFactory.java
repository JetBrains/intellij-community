package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating {@link SearchEverywhereContributor} entities.
 * @param <Item> type of elements returned by {@link SearchEverywhereContributor}
 */
public interface SearchEverywhereContributorFactory<Item> {

  /**
   * Creates {@link SearchEverywhereContributor} instance.
   * @param initEvent initial event which led to method call
   */
  @NotNull
  SearchEverywhereContributor<Item> createContributor(@NotNull AnActionEvent initEvent);

  /**
   * Returns 'true' if the contributor is available and should be created
   */
  default boolean isAvailable() {
    return true;
  }

  /**
   * Not used and going to be deleted next releases.
   * @deprecated to be removed in IDEA 2022.2
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  default SearchEverywhereTabDescriptor getTab() {
    return SearchEverywhereTabDescriptor.PROJECT;
  }
}
