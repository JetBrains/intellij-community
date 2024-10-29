// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public class SelectionAwareListCellRenderer<T> implements ListCellRenderer<T> {
  private final NotNullFunction<? super T, ? extends JComponent> myFun;

  public SelectionAwareListCellRenderer(NotNullFunction<? super T, ? extends JComponent> fun) {myFun = fun;}

  @Override
  public @NotNull Component getListCellRendererComponent(@NotNull JList list,
                                                         Object value,
                                                         int index,
                                                         boolean isSelected,
                                                         boolean cellHasFocus) {
    @SuppressWarnings("unchecked")
    final JComponent comp = myFun.fun((T)value);
    comp.setOpaque(true);
    if (isSelected) {
      comp.setBackground(list.getSelectionBackground());
      comp.setForeground(list.getSelectionForeground());
    }
    else {
      comp.setBackground(list.getBackground());
      comp.setForeground(list.getForeground());
    }
    for (JLabel label : UIUtil.findComponentsOfType(comp, JLabel.class)) {
      label.setForeground(UIUtil.getListForeground(isSelected));
    }
    return comp;
  }
}
