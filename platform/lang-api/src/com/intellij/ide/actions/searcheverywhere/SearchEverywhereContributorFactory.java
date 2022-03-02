package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
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
   * @deprecated see {@link #isAvailable(Project)}
   */
  @Deprecated
  default boolean isAvailable() {
    return true;
  }

  /**
   * Returns 'true' if the contributor is available and should be created
   */
  default boolean isAvailable(Project project) {
    return isAvailable();
  }

  /**
   * Not used and going to be deleted next releases.
   * @deprecated to be removed in IDEA 2022.2
   */
  @NotNull
  @Deprecated(forRemoval = true)
  default SearchEverywhereTabDescriptor getTab() {
    return SearchEverywhereTabDescriptor.PROJECT;
  }
}
