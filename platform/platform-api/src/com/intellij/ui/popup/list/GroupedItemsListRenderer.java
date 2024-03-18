// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class GroupedItemsListRenderer<E> extends GroupedElementsRenderer.List implements ListCellRenderer<E> {
  protected ListItemDescriptor<E> myDescriptor;

  protected JLabel myNextStepLabel;
  protected int myCurrentIndex;

  public JLabel getNextStepLabel() {
    return myNextStepLabel;
  }


  public GroupedItemsListRenderer(ListItemDescriptor<E> descriptor) {
    myDescriptor = descriptor;
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
    String caption = myDescriptor.getCaptionAboveOf(value);
    boolean hasSeparator = hasSeparator(value, index);
    Icon icon = getItemIcon(value, isSelected);
    final JComponent result = configureComponent(myDescriptor.getTextFor(value), myDescriptor.getTooltipFor(value),
                                                 icon, icon, isSelected, hasSeparator,
                                                 caption, -1);
    myCurrentIndex = index;
    myRendererComponent.setBackground(list.getBackground());
    customizeComponent(list, value, index, isSelected, cellHasFocus);

    if (ExperimentalUI.isNewUI() && getItemComponent() instanceof SelectablePanel selectablePanel) {
      selectablePanel.setSelectionColor(isSelected ? JBUI.CurrentTheme.List.background(true, true) : null);
    }

    return result;
  }

  @ApiStatus.Internal
  protected boolean hasSeparator(E value, int index) {
    String caption = myDescriptor.getCaptionAboveOf(value);
    if (index == 0 && StringUtil.isEmptyOrSpaces(caption)) {
      return false;
    }
    return myDescriptor.hasSeparatorAboveOf(value);
  }

  protected @Nullable Icon getItemIcon(E value, boolean isSelected) {
    return isSelected ? IconUtil.wrapToSelectionAwareIcon(myDescriptor.getSelectedIconFor(value)) : myDescriptor.getIconFor(value);
  }

  @Override
  protected JComponent createItemComponent() {
    createLabel();
    return layoutComponent(myTextLabel);
  }

  protected void createLabel() {
    myTextLabel = new ErrorLabel();
    myTextLabel.setBorder(ExperimentalUI.isNewUI() ? JBUI.Borders.empty() : JBUI.Borders.emptyBottom(1));
    myTextLabel.setOpaque(true);
  }

  protected JComponent layoutComponent(JComponent middleItemComponent) {
    myNextStepLabel = new JLabel();
    myNextStepLabel.setOpaque(false);

    if (ExperimentalUI.isNewUI()) {
      SelectablePanel result = SelectablePanel.wrap(middleItemComponent);
      PopupUtil.configListRendererFlexibleHeight(result);
      result.add(myNextStepLabel, BorderLayout.EAST);
      return result;
    }
    else {
      return JBUI.Panels.simplePanel(middleItemComponent)
        .addToRight(myNextStepLabel)
        .withBorder(getDefaultItemComponentBorder());

    }
  }

  protected void customizeComponent(JList<? extends E> list, E value, boolean isSelected) {
  }

  protected void customizeComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
    customizeComponent(list, value, isSelected);
  }
}
