// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public interface SearchEverywhereContributor<Item, Filter> {

  ExtensionPointName<SearchEverywhereContributorFactory<?, ?>> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereContributor");

  @NotNull
  String getSearchProviderId();

  @NotNull
  String getGroupName();

  @NotNull
  default String getFullGroupName() {
    return getGroupName();
  }

  @Nullable
  String includeNonProjectItemsText();

  int getSortWeight();

  boolean showInFindResults();

  default boolean isShownInSeparateTab() {
    return false;
  }

  default int getElementPriority(@NotNull Item element, @NotNull String searchPattern) {
    return 0;
  }

  @NotNull
  default List<SearchEverywhereCommandInfo> getSupportedCommands() {
    return Collections.emptyList();
  }

  @Nullable
  default String getAdvertisement() { return null; }

  void fetchElements(@NotNull String pattern,
                     boolean everywhere,
                     @Nullable SearchEverywhereContributorFilter<Filter> filter,
                     @NotNull ProgressIndicator progressIndicator,
                     @NotNull Processor<? super Item> consumer);

  @NotNull
  default ContributorSearchResult<Item> search(@NotNull String pattern,
                                               boolean everywhere,
                                               @Nullable SearchEverywhereContributorFilter<Filter> filter,
                                               @NotNull ProgressIndicator progressIndicator,
                                               int elementsLimit) {
    ContributorSearchResult.Builder<Item> builder = ContributorSearchResult.builder();
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
  default List<Item> search(@NotNull String pattern,
                            boolean everywhere,
                            @Nullable SearchEverywhereContributorFilter<Filter> filter,
                            @NotNull ProgressIndicator progressIndicator) {
    List<Item> res = new ArrayList<>();
    fetchElements(pattern, everywhere, filter, progressIndicator, o -> res.add(o));
    return res;
  }

  boolean processSelectedItem(@NotNull Item selected, int modifiers, @NotNull String searchText);

  @NotNull
  ListCellRenderer<? super Item> getElementsRenderer();

  @Nullable
  Object getDataForItem(@NotNull Item element, @NotNull String dataId);

  @NotNull
  default String filterControlSymbols(@NotNull String pattern) {
    return pattern;
  }

  default boolean isMultiSelectionSupported() {
    return false;
  }

  default boolean isDumbModeSupported() {
    return true;
  }

  default boolean isEmptyPatternSupported() {
    return false;
  }
}
