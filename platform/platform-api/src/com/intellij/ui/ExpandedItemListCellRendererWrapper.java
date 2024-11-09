// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class ExpandedItemListCellRendererWrapper<T> implements ListCellRenderer<T> {
  private final @NotNull ListCellRenderer<? super T> myWrappee;
  private final @NotNull ExpandableItemsHandler<Integer> myHandler;

  public ExpandedItemListCellRendererWrapper(@NotNull ListCellRenderer<? super T> wrappee, @NotNull ExpandableItemsHandler<Integer> handler) {
    myWrappee = wrappee;
    myHandler = handler;
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected, boolean cellHasFocus) {
    GraphicsUtil.setAntialiasingType(list, AntialiasingType.getAATextInfoForSwingComponent());
    Component result = myWrappee.getListCellRendererComponent(list, UIUtil.htmlInjectionGuard(value), index, isSelected, cellHasFocus);
    if (!myHandler.getExpandedItems().contains(index)) return result;
    Rectangle bounds = result.getBounds();
    ExpandedItemRendererComponentWrapper wrapper = ExpandedItemRendererComponentWrapper.wrap(result);
    if (ClientProperty.isTrue(list, ExpandableItemsHandler.EXPANDED_RENDERER)) {
      if (ClientProperty.isTrue(result, ExpandableItemsHandler.USE_RENDERER_BOUNDS)) {
        wrapper.setBounds(bounds);
        wrapper.putClientProperty(ExpandableItemsHandler.USE_RENDERER_BOUNDS, true);
      }
    }
    wrapper.owner = list;
    return wrapper;
  }

  @Override
  public String toString() {
    return "ExpandedItemListCellRendererWrapper[" + getWrappee().getClass().getName() + "]";
  }

  public @NotNull ListCellRenderer getWrappee() {
    return myWrappee;
  }

  public static <T> ListCellRenderer<T> unwrap(ListCellRenderer<T> renderer) {
    if (renderer instanceof ExpandedItemListCellRendererWrapper wrapper) {
      //noinspection unchecked
      return wrapper.getWrappee();
    }
    return renderer;
  }
}
