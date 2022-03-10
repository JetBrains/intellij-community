// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
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

    Map<String, Integer> priorities = new HashMap<>();
    for (int i = 0; i < prioritizedContributors.size(); i++) {
      priorities.put(prioritizedContributors.get(i), prioritizedContributors.size() - i);
    }

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
          Component component = myMoreRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          component.setPreferredSize(UIUtil.updateListRowHeight(component.getPreferredSize()));
          return component;
        }

        Component component = SearchEverywhereClassifier.EP_Manager.getListCellRendererComponent(
          list, value, index, isSelected, cellHasFocus);
        if (component == null) {
          SearchEverywhereContributor<Object> contributor = model.getContributorForIndex(index);
          assert contributor != null : "Null contributor is not allowed here";
          ListCellRenderer<? super Object> renderer = myRenderersCache.computeIfAbsent(contributor.getSearchProviderId(), s -> contributor.getElementsRenderer());
          component = renderer.getListCellRendererComponent(list, value, index, isSelected, true);
        }

        if (component instanceof JComponent) {
          Border border = ((JComponent)component).getBorder();
          if (border != GotoActionModel.GotoActionListCellRenderer.TOGGLE_BUTTON_BORDER) {
            ((JComponent)component).setBorder(JBUI.Borders.empty(1, 2));
          }
        }

        if (!isSelected && component.getBackground() == UIUtil.getListBackground()) {
          PopupUtil.applyNewUIBackground(component);
        }

        AppUIUtil.targetToDevice(component, list);
        component.setPreferredSize(UIUtil.updateListRowHeight(component.getPreferredSize()));

        return component;
      }
    };
  }
}
