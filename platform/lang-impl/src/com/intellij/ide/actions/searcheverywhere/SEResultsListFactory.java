// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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

}
