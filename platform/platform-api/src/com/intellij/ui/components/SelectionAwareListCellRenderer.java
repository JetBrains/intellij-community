// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

@ApiStatus.Internal
public class SelectionAwareListCellRenderer<T> implements ListCellRenderer<T> {
  private final Function<? super T, ? extends @NotNull JComponent> myFun;

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public SelectionAwareListCellRenderer(Function<? super T, ? extends @NotNull JComponent> fun) {
    myFun = fun;
  }

  /** @deprecated use {@link #SelectionAwareListCellRenderer(Function)} instead */
  @Deprecated
  @SuppressWarnings({"LambdaUnfriendlyMethodOverload", "UnnecessaryFullyQualifiedName", "UsagesOfObsoleteApi"})
  public SelectionAwareListCellRenderer(com.intellij.util.NotNullFunction<? super T, ? extends JComponent> fun) {
    myFun = fun;
  }

  @Override
  public @NotNull Component getListCellRendererComponent(@NotNull JList list, Object v, int index, boolean selected, boolean focused) {
    @SuppressWarnings("unchecked")
    var comp = myFun.apply((T)v);
    comp.setOpaque(true);
    if (selected) {
      comp.setBackground(list.getSelectionBackground());
      comp.setForeground(list.getSelectionForeground());
    }
    else {
      comp.setBackground(list.getBackground());
      comp.setForeground(list.getForeground());
    }
    for (var label : UIUtil.findComponentsOfType(comp, JLabel.class)) {
      label.setForeground(UIUtil.getListForeground(selected, focused));
    }
    return comp;
  }
}
