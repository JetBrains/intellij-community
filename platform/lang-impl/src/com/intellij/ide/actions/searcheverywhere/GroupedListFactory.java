// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.util.Computable;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

final class GroupedListFactory extends SEResultsListFactory {

  private final GroupTitleRenderer myGroupTitleRenderer = new GroupTitleRenderer();

  @Override
  public SearchListModel createModel(Computable<String> tabIDProvider) {
    return new GroupedSearchListModel();
  }

  @Override
  public JBList<Object> createList(SearchListModel model) {
    return new JBList<>(model);
  }

  @Override
  ListCellRenderer<Object> createListRenderer(SearchListModel model, SearchEverywhereHeader header) {
    GroupedSearchListModel groupedModel = (GroupedSearchListModel)model;
    return new ListCellRenderer<>() {

      private final Map<String, ListCellRenderer<? super Object>> myRenderersCache = new HashMap<>();

      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == SearchListModel.MORE_ELEMENT) {
          return getMoreElementRenderer(list, index, isSelected, cellHasFocus);
        }

        Component component = getNonMoreElementRenderer(list, value, index, isSelected, cellHasFocus, groupedModel, myRenderersCache);

        if (!header.getSelectedTab().isSingleContributor() && groupedModel.isGroupFirstItem(index)) {
          SearchEverywhereContributor<Object> contributor = groupedModel.getContributorForIndex(index);
          //noinspection ConstantConditions
          component = myGroupTitleRenderer.withDisplayedData(contributor.getFullGroupName(), component);
        }

        return component;
      }
    };
  }
}
