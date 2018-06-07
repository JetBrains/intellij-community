/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
//todo generic? #UX-1
public interface SearchEverywhereContributor<F> {

  ExtensionPointName<SearchEverywhereContributorFactory<?>> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereContributor");

  @NotNull
  String getSearchProviderId();

  @NotNull
  String getGroupName();

  String includeNonProjectItemsText();

  int getSortWeight();

  boolean showInFindResults();

  ContributorSearchResult<Object> search(String pattern, boolean everywhere, SearchEverywhereContributorFilter<F> filter,
                                         ProgressIndicator progressIndicator, int elementsLimit);

  default List<Object> search(String pattern, boolean everywhere, SearchEverywhereContributorFilter<F> filter,
                              ProgressIndicator progressIndicator) {
    return search(pattern, everywhere, filter, progressIndicator, -1).getItems();
  }

  boolean processSelectedItem(Object selected, int modifiers);

  ListCellRenderer getElementsRenderer(JList<?> list);

  Object getDataForItem(Object element, String dataId);

  static List<SearchEverywhereContributorFactory<?>> getProviders() {
    return Arrays.asList(EP_NAME.getExtensions());
  }
}
