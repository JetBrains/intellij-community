/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ExpandedItemListCellRendererWrapper<T> implements ListCellRenderer<T> {
  @NotNull private final ListCellRenderer<T> myWrappee;
  @NotNull private final ExpandableItemsHandler<Integer> myHandler;

  public ExpandedItemListCellRendererWrapper(@NotNull ListCellRenderer<T> wrappee, @NotNull ExpandableItemsHandler<Integer> handler) {
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
        UIUtil.putClientProperty(wrapper, ExpandableItemsHandler.USE_RENDERER_BOUNDS, true);
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
