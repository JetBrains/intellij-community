// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

class MixedListFactory extends SEResultsListFactory {

  private final List<String> prioritizedContributors = new ArrayList<>();

  MixedListFactory() {
    prioritizedContributors.add(CalculatorSEContributor.class.getName());
    prioritizedContributors.add("AutocompletionContributor");
    prioritizedContributors.add("CommandsContributor");
    prioritizedContributors.add(TopHitSEContributor.class.getSimpleName());
    if (Registry.is("search.everywhere.recent.at.top")) {
      prioritizedContributors.add(RecentFilesSEContributor.class.getSimpleName());
    }
  }

  @Override
  public SearchListModel createModel() {
    MixedSearchListModel mixedModel = new MixedSearchListModel();

    Map<String, Integer> priorities = getContributorsPriorities();
    Comparator<SearchEverywhereFoundElementInfo> prioritizedContributorsComparator = (element1, element2) -> {
      int firstElementPriority = priorities.getOrDefault(element1.getContributor().getSearchProviderId(), 0);
      int secondElementPriority = priorities.getOrDefault(element2.getContributor().getSearchProviderId(), 0);
      return Integer.compare(firstElementPriority, secondElementPriority);
    };

    Comparator<SearchEverywhereFoundElementInfo> comparator = prioritizedContributorsComparator
      .thenComparing(SearchEverywhereFoundElementInfo.COMPARATOR)
      .reversed();
    mixedModel.setElementsComparator(comparator);

    return mixedModel;
  }

  @NotNull
  public Map<String, Integer> getContributorsPriorities() {
    Map<String, Integer> priorities = new HashMap<>();
    for (int i = 0; i < prioritizedContributors.size(); i++) {
      priorities.put(prioritizedContributors.get(i), prioritizedContributors.size() - i);
    }
    return priorities;
  }

  @Override
  public JBList<Object> createList(SearchListModel model) {
    return new JBList<>(model);
  }

  @Override
  ListCellRenderer<Object> createListRenderer(SearchListModel model, SearchEverywhereHeader header) {
    return new ListCellRenderer<>() {

      private final Map<String, ListCellRenderer<? super Object>> myRenderersCache = new HashMap<>();

      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == SearchListModel.MORE_ELEMENT) {
          return getMoreElementRenderer(list, index, isSelected, cellHasFocus);
        }

        return getNonMoreElementRenderer(list, value, index, isSelected, cellHasFocus, model, myRenderersCache);
      }
    };
  }
}
