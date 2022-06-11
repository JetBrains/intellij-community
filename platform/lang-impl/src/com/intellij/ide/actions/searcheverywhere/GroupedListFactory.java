// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

class GroupedListFactory extends SEResultsListFactory {
  @Override
  public SearchListModel createModel() {
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
          Component component = myMoreRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          component.setPreferredSize(UIUtil.updateListRowHeight(component.getPreferredSize()));
          return component;
        }

        SearchEverywhereContributor<Object> contributor = groupedModel.getContributorForIndex(index);
        Component component = SearchEverywhereClassifier.EP_Manager.getListCellRendererComponent(
          list, value, index, isSelected, cellHasFocus);
        if (component == null) {
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
        AppUIUtil.targetToDevice(component, list);
        component.setPreferredSize(UIUtil.updateListRowHeight(component.getPreferredSize()));

        if (!isSelected && component.getBackground() == UIUtil.getListBackground()) {
          PopupUtil.applyNewUIBackground(component);
        }

        if (!header.getSelectedTab().isSingleContributor() && groupedModel.isGroupFirstItem(index)) {
          //noinspection ConstantConditions
          component = myGroupTitleRenderer.withDisplayedData(contributor.getFullGroupName(), component);
        }

        return component;
      }
    };
  }

  private final GroupTitleRenderer myGroupTitleRenderer = new GroupTitleRenderer();

  private static class GroupTitleRenderer extends CellRendererPanel {

    private final SimpleColoredComponent titleLabel = new SimpleColoredComponent();

    GroupTitleRenderer() {
      setLayout(new BorderLayout());
      SeparatorComponent separatorComponent = new SeparatorComponent(
        titleLabel.getPreferredSize().height / 2, JBUI.CurrentTheme.BigPopup.listSeparatorColor(), null);

      JPanel topPanel = JBUI.Panels.simplePanel(5, 0)
        .addToCenter(separatorComponent)
        .addToLeft(titleLabel)
        .withBorder(JBUI.Borders.empty(1, 7))
        .withBackground(ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Popup.BACKGROUND : UIUtil.getListBackground());
      add(topPanel, BorderLayout.NORTH);
    }

    public GroupTitleRenderer withDisplayedData(@Nls String title, Component itemContent) {
      titleLabel.clear();
      titleLabel.append(title, SMALL_LABEL_ATTRS);
      Component prevContent = ((BorderLayout)getLayout()).getLayoutComponent(BorderLayout.CENTER);
      if (prevContent != null) {
        remove(prevContent);
      }
      add(itemContent, BorderLayout.CENTER);
      accessibleContext = itemContent.getAccessibleContext();

      return this;
    }
  }
}
