package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating {@link SearchEverywhereContributor} entities.
 * @param <Item> type of elements returned by {@link SearchEverywhereContributor}
 * @deprecated The old Search Everywhere API is being sunset.
 * Implement {@code com.intellij.platform.searchEverywhere.SeItemsProviderFactory} instead.
 */
@Deprecated
public interface SearchEverywhereContributorFactory<Item> {

  /**
   * Creates {@link SearchEverywhereContributor} instance.
   * @param initEvent initial event which led to method call
   */
  @NotNull
  SearchEverywhereContributor<Item> createContributor(@NotNull AnActionEvent initEvent);

  /**
   * Returns 'true' if the contributor is available and should be created for the current Search Everywhere mode.
   * <p>
   * This internal overload exists because some contributors should participate only in legacy Search Everywhere
   * or only in split Search Everywhere, while {@link #isAvailable(Project)} does not expose that distinction.
   * <p>
   * The default implementation delegates to {@link #isAvailable(Project)} to keep existing contributors working
   * without changes.
   */
  @ApiStatus.Internal
  default boolean isAvailable(Project project, boolean isSplitSearchEverywhere) {
    return isAvailable(project);
  }

  /**
   * Returns 'true' if the contributor is available and should be created
   */
  default boolean isAvailable(Project project) {
    return true;
  }
}
