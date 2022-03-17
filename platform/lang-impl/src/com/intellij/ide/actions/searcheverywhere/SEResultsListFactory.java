// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Supplier;

abstract class SEResultsListFactory {

  abstract SearchListModel createModel();

  abstract JBList<Object> createList(SearchListModel model);

  abstract ListCellRenderer<Object> createListRenderer(SearchListModel model, SearchEverywhereHeader header);

  protected static final SimpleTextAttributes SMALL_LABEL_ATTRS = new SimpleTextAttributes(
    SimpleTextAttributes.STYLE_SMALLER, JBUI.CurrentTheme.BigPopup.listTitleLabelForeground());

  protected static final ListCellRenderer<Object> myMoreRenderer = new ColoredListCellRenderer<>() {

    @Override
    protected int getMinHeight() {
      return -1;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value != SearchListModel.MORE_ELEMENT) {
        throw new AssertionError(value);
      }
      setFont(StartupUiUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL)));
      append(IdeBundle.message("search.everywhere.points.more"), SMALL_LABEL_ATTRS);
      setIpad(JBInsets.create(1, 7));
      setMyBorder(null);
    }
  };

  @ApiStatus.Internal
  Component getMoreElementRenderer(@NotNull JList<?> list, int index, boolean selected, boolean hasFocus) {
    Component component = myMoreRenderer.getListCellRendererComponent(list, SearchListModel.MORE_ELEMENT, index, selected, hasFocus);
    if (!ExperimentalUI.isNewUI()) {
      component.setPreferredSize(UIUtil.updateListRowHeight(component.getPreferredSize()));
      return component;
    }
    SelectablePanel selectablePanel = SelectablePanel.wrap(component, JBUI.CurrentTheme.Popup.BACKGROUND);
    PopupUtil.configSelectablePanel(selectablePanel);
    if (selected) {
      selectablePanel.setSelectionColor(UIUtil.getListBackground(true, true));
    }
    return selectablePanel;
  }

  @ApiStatus.Internal
  Component getNonMoreElementRenderer(@NotNull JList<?> list, Object value, int index, boolean selected, boolean hasFocus,
                                      SearchListModel searchListModel, Map<String, ListCellRenderer<? super Object>> renderersCache) {
    Color unselectedBackground = extractUnselectedBackground(selected, () ->
      SearchEverywhereClassifier.EP_Manager.getListCellRendererComponent(list, value, index, false, hasFocus));
    Component component = SearchEverywhereClassifier.EP_Manager.getListCellRendererComponent(
      list, value, index, selected, hasFocus);
    if (component == null) {
      SearchEverywhereContributor<Object> contributor = searchListModel.getContributorForIndex(index);
      assert contributor != null : "Null contributor is not allowed here";
      ListCellRenderer<? super Object> renderer = renderersCache.computeIfAbsent(contributor.getSearchProviderId(), s -> contributor.getElementsRenderer());
      unselectedBackground = extractUnselectedBackground(selected, () ->
        renderer.getListCellRendererComponent(list, value, index, false, true));
      component = renderer.getListCellRendererComponent(list, value, index, selected, true);
    }

    if (component instanceof JComponent) {
      JComponent jComponent = (JComponent)component;
      if (ExperimentalUI.isNewUI()) {
        jComponent.setBorder(JBUI.Borders.empty());
      }
      else {
        if (jComponent.getBorder() != GotoActionModel.GotoActionListCellRenderer.TOGGLE_BUTTON_BORDER) {
          jComponent.setBorder(JBUI.Borders.empty(1, 2));
        }
      }
    }

    AppUIUtil.targetToDevice(component, list);

    if (ExperimentalUI.isNewUI()) {
      Color rowBackground = selected ? unselectedBackground : component.getBackground();
      if (rowBackground == null || rowBackground == UIUtil.getListBackground()) {
        rowBackground = JBUI.CurrentTheme.Popup.BACKGROUND;
      }
      SelectablePanel selectablePanel = SelectablePanel.wrap(component, rowBackground);
      PopupUtil.configSelectablePanel(selectablePanel);
      if (selected) {
        selectablePanel.setSelectionColor(UIUtil.getListBackground(true, true));
      }
      UIUtil.setOpaqueRecursively(component, false);
      component = selectablePanel;
    } else {
      component.setPreferredSize(UIUtil.updateListRowHeight(component.getPreferredSize()));
    }

    return component;
  }

  private static @Nullable Color extractUnselectedBackground(boolean isSelected, Supplier<Component> supplier) {
    if (ExperimentalUI.isNewUI() && isSelected) {
      Component component = supplier.get();
      if (component != null) {
        return component.getBackground();
      }
    }
    return null;
  }
}
