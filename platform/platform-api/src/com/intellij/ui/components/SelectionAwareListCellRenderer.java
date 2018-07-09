// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class SelectionAwareListCellRenderer<T> extends DefaultListCellRenderer {
  private final NotNullFunction<T, JComponent> myFun;

  public SelectionAwareListCellRenderer(NotNullFunction<T, JComponent> fun) {myFun = fun;}

  @NotNull
  @Override
  public Component getListCellRendererComponent(@NotNull JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    @SuppressWarnings({"unchecked"})
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
