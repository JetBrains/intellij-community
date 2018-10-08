/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author Konstantin Bulenkov
 */
public interface SearchEverywhereContributor<F> {

  ExtensionPointName<SearchEverywhereContributorFactory<?>> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereContributor");

  @NotNull
  String getSearchProviderId();

  @NotNull
  String getGroupName();

  @Nullable
  String includeNonProjectItemsText();

  int getSortWeight();

  boolean showInFindResults();

  default boolean isShownInSeparateTab() {
    return false;
  }

  default int getElementPriority(@NotNull Object element, @NotNull String searchPattern) {
    return 0;
  }

  @NotNull
  default List<SearchEverywhereCommandInfo> getSupportedCommands() {
    return Collections.emptyList();
  }

  void fetchElements(@NotNull String pattern,
                     boolean everywhere,
                     @Nullable SearchEverywhereContributorFilter<F> filter,
                     @NotNull ProgressIndicator progressIndicator,
                     @NotNull Function<Object, Boolean> consumer);

  @NotNull
  default ContributorSearchResult<Object> search(@NotNull String pattern,
                                                 boolean everywhere,
                                                 @Nullable SearchEverywhereContributorFilter<F> filter,
                                                 @NotNull ProgressIndicator progressIndicator,
                                                 int elementsLimit) {
    ContributorSearchResult.Builder<Object> builder = ContributorSearchResult.builder();
    fetchElements(pattern, everywhere, filter, progressIndicator, element -> {
      if (elementsLimit < 0 || builder.itemsCount() < elementsLimit) {
        builder.addItem(element);
        return true;
      }
      else {
        builder.setHasMore(true);
        return false;
      }
    });

    return builder.build();
  }

  @NotNull
  default List<Object> search(@NotNull String pattern,
                              boolean everywhere,
                              @Nullable SearchEverywhereContributorFilter<F> filter,
                              @NotNull ProgressIndicator progressIndicator) {
    List<Object> res = new ArrayList<>();
    fetchElements(pattern, everywhere, filter, progressIndicator, o -> res.add(o));
    return res;
  }

  boolean processSelectedItem(@NotNull Object selected, int modifiers, @NotNull String searchText);

  @NotNull
  ListCellRenderer getElementsRenderer(@NotNull JList<?> list);

  @Nullable
  Object getDataForItem(@NotNull Object element, @NotNull String dataId);

  @NotNull
  default String filterControlSymbols(@NotNull String pattern) {
    return pattern;
  }

  default boolean isMultiselectSupported() {
    return false;
  }

  default boolean isDumbModeSupported() {
    return true;
  }

  @NotNull
  static List<SearchEverywhereContributorFactory<?>> getProviders() {
    return Arrays.asList(EP_NAME.getExtensions());
  }
}
