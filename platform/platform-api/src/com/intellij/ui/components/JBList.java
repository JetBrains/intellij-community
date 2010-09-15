/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.ui.ComponentWithExpandableItems;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.ExpandableItemsHandlerFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public class JBList extends JList implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer> {
  private StatusText myEmptyText;
  private ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  public JBList() {
    init();
  }

  public JBList(ListModel dataModel) {
    super(dataModel);
    init();
  }

  public JBList(Object... listData) {
    super(listData);
    init();
  }

  public JBList(Collection items) {
    this(ArrayUtil.toObjectArray(items));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myEmptyText.paint(this, g);
  }

  private void init() {
    setSelectionBackground(UIUtil.getListSelectionBackground());
    setSelectionForeground(UIUtil.getListSelectionForeground());

    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return JBList.this.isEmpty();
      }
    };


    if (shouldInstallItemTooltipExpander()) myExpandableItemsHandler = ExpandableItemsHandlerFactory.install(this);
  }

  protected boolean shouldInstallItemTooltipExpander() {
    return true;
  }


  public boolean isEmpty() {
    return getItemsCount() == 0;
  }

  public int getItemsCount() {
    ListModel model = getModel();
    return model == null ? 0 : model.getSize();
  }

  @Override
  public StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  @NotNull
  public ExpandableItemsHandler<Integer> getExpandableItemsHandler() {
    return myExpandableItemsHandler;
  }

  public <T> void installCellRenderer(final @NotNull NotNullFunction<T, JComponent> fun) {
    setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        @SuppressWarnings({"unchecked"})
        final JComponent comp = fun.fun((T)value);  
        comp.setOpaque(true);
        if (isSelected) {
          comp.setBackground(list.getSelectionBackground());
          comp.setForeground(list.getSelectionForeground());
        } else {
          comp.setBackground(list.getBackground());
          comp.setForeground(list.getForeground());
        }
        return comp;
      }
    });
  }
}
