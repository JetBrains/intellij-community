/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
//todo generic? #UX-1
public interface SearchEverywhereContributor {

  String ALL_CONTRIBUTORS_GROUP_ID = SearchEverywhereContributor.class.getSimpleName() + ".All";

  ExtensionPointName<SearchEverywhereContributor> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereContributor");

  @NotNull
  String getSearchProviderId();

  @NotNull
  String getGroupName();

  default String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.include.non.project.items", IdeUICustomization.getInstance().getProjectConceptName());
  }

  int getSortWeight();

  default ContributorSearchResult search(Project project, String pattern, boolean everywhere, ProgressIndicator progressIndicator, int elementsLimit) {
    return new ContributorSearchResult(Collections.emptyList(), false);
  }

  default List<Object> search(Project project, String pattern, boolean everywhere, ProgressIndicator progressIndicator) {
    return search(project, pattern, everywhere, progressIndicator, -1).getItems();
  }

  //todo return boolean #UX-1
  void processSelectedItem(Object selected, int modifiers);

  ListCellRenderer getElementsRenderer(Project project);

  static List<SearchEverywhereContributor> getProvidersSorted() {
    return Arrays.stream(EP_NAME.getExtensions())
      .sorted(Comparator.comparingInt(SearchEverywhereContributor::getSortWeight))
      .collect(Collectors.toList());
  }
}
