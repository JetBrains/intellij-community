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
package com.intellij.ui.components;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicListUI;

/**
 * @author Sergey.Malenkov
 */
final class WideSelectionListUI extends BasicListUI {
  private Rectangle myPaintBounds;

  @Override
  public void paint(Graphics g, JComponent c) {
    myPaintBounds = g.getClipBounds();
    super.paint(g, c);
  }

  @Override
  protected void paintCell(Graphics g,
                           int row,
                           Rectangle rowBounds,
                           ListCellRenderer renderer,
                           ListModel model,
                           ListSelectionModel selectionModel,
                           int leadSelectionIndex) {
    if (list.getLayoutOrientation() != JList.VERTICAL) {
      // If the list layout orientation is VERTICAL_WRAP or HORIZONTAL_WRAP
      // we don't want to paint anything outside the cell to avoid covering other cells on the same row
      super.paintCell(g, row, rowBounds, renderer, model, selectionModel, leadSelectionIndex);
      return;
    }

    Rectangle paintBounds = myPaintBounds;
    if (paintBounds != null) {
      boolean selected = selectionModel.isSelectedIndex(row);
      boolean focused = row == leadSelectionIndex && list.hasFocus();
      @SuppressWarnings("unchecked")
      Component component = renderer.getListCellRendererComponent(list, model.getElementAt(row), row, selected, focused);
      if (component != null) {
        if (rendererPane != component.getParent()) rendererPane.add(component);
        g.setClip(paintBounds.x, paintBounds.y, paintBounds.width, paintBounds.height);
        paintRenderer(g, rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, list, component);
        g.clipRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
      }
    }
    super.paintCell(g, row, rowBounds, renderer, model, selectionModel, leadSelectionIndex);
  }

  private static void paintRenderer(Graphics g, int x, int y, int width, int height, Component owner, Component renderer) {
    g.clipRect(0, y, owner.getWidth(), height);
    paintBackground(g, y, height, owner, renderer);
    if (renderer instanceof Container) {
      Component[] children;
      Container container = (Container)renderer;
      synchronized (container.getTreeLock()) {
        children = container.getComponents();
      }
      if (children.length > 0) {
        renderer.setBounds(x, y, width, height);
        renderer.validate();
        for (Component child : children) {
          if (0 == child.getX() && width == child.getWidth() && 0 < child.getHeight()) {
            paintBackground(g, y + child.getY(), child.getHeight(), owner, child);
          }
        }
      }
    }
  }

  private static void paintBackground(Graphics g, int y, int height, Component owner, Component child) {
    if (child.isOpaque()) {
      Color color = child.getBackground();
      if (color != null && !color.equals(owner.getBackground())) {
        g.setColor(color);
        g.fillRect(0, y, owner.getWidth(), height);
      }
    }
  }
}
