// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ExpandedItemListCellRendererWrapper<T> implements ListCellRenderer<T> {
  @NotNull private final ListCellRenderer<? super T> myWrappee;
  @NotNull private final ExpandableItemsHandler<Integer> myHandler;

  public ExpandedItemListCellRendererWrapper(@NotNull ListCellRenderer<? super T> wrappee, @NotNull ExpandableItemsHandler<Integer> handler) {
    myWrappee = wrappee;
    myHandler = handler;
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected, boolean cellHasFocus) {
    GraphicsUtil.setAntialiasingType(list, AntialiasingType.getAAHintForSwingComponent());
    Component result = myWrappee.getListCellRendererComponent(list, UIUtil.htmlInjectionGuard(value), index, isSelected, cellHasFocus);
    if (!myHandler.getExpandedItems().contains(index)) return result;
    Rectangle bounds = result.getBounds();
    ExpandedItemRendererComponentWrapper wrapper = ExpandedItemRendererComponentWrapper.wrap(result);
    if (UIUtil.isClientPropertyTrue(list, ExpandableItemsHandler.EXPANDED_RENDERER)) {
      if (UIUtil.isClientPropertyTrue(result, ExpandableItemsHandler.USE_RENDERER_BOUNDS)) {
        wrapper.setBounds(bounds);
        ComponentUtil.putClientProperty(wrapper, ExpandableItemsHandler.USE_RENDERER_BOUNDS, true);
      }
    }
    wrapper.owner = list;
    return wrapper;
  }

  @Override
  public String toString() {
    return "ExpandedItemListCellRendererWrapper[" + getWrappee().getClass().getName() + "]";
  }

  @NotNull
  public ListCellRenderer getWrappee() {
    return myWrappee;
  }
}
