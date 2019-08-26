// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.util.ColoredItem;
import com.intellij.ui.BackgroundSupplier;
import com.intellij.ui.list.ListCellBackgroundSupplier;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.*;

/**
 * @author Sergey.Malenkov
 * @noinspection ALL
 */
public final class WideSelectionListUI extends BasicListUI {
  private Rectangle myPaintBounds;

  @Override
  public void paint(Graphics g, JComponent c) {
    // do not paint a line background if layout orientation is not vertical
    myPaintBounds = JList.VERTICAL != list.getLayoutOrientation() ? null : g.getClipBounds();
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
    if (!(0 <= row && row < model.getSize())) return;
    Rectangle paintBounds = myPaintBounds;
    if (paintBounds != null) {
      boolean selected = selectionModel.isSelectedIndex(row);
      boolean focused = row == leadSelectionIndex && (!list.isFocusable() || list.hasFocus());
      Object value = model.getElementAt(row);
      Color background = getBackground(list, value, row);
      if (background != null) {
        g.setColor(background);
        g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
      }
      Component component = renderer.getListCellRendererComponent(list, value, row, selected, focused);
      if (component != null) {
        if (rendererPane != component.getParent()) rendererPane.add(component);
        g.setClip(paintBounds.x, paintBounds.y, paintBounds.width, paintBounds.height);
        paintRenderer(g, rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, list, component);
        g.clipRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
      }
    }
    super.paintCell(g, row, rowBounds, renderer, model, selectionModel, leadSelectionIndex);
  }

  @Nullable
  private static Color getBackground(@NotNull JList<Object> list, @Nullable Object value, int row) {
    if (value instanceof ColoredItem) {
      Color background = ((ColoredItem)value).getColor();
      if (background != null) return background;
    }
    if (value instanceof BackgroundSupplier) {
      BackgroundSupplier supplier = (BackgroundSupplier)value;
      Color background = supplier.getElementBackground(row);
      if (background != null) return background;
    }
    if (list instanceof ListCellBackgroundSupplier) {
      //noinspection unchecked
      Color background = ((ListCellBackgroundSupplier<Object>)list).getCellBackground(value, row);
      if (background != null) return background;
    }
    return null;
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

  @Override
  public Rectangle getCellBounds(JList list, int index1, int index2) {
    Rectangle bounds = super.getCellBounds(list, index1, index2);
    if (bounds != null && index1 == index2 && list instanceof JBList && JList.VERTICAL == list.getLayoutOrientation()) {
      if (((JBList<?>)list).getExpandableItemsHandler().getExpandedItems().contains(index1)) {
        // increase paint area for list item with shown extendable popup
        JScrollPane pane = JBScrollPane.findScrollPane(list);
        JScrollBar bar = pane == null ? null : pane.getVerticalScrollBar();
        if (bar != null && !bar.isOpaque()) bounds.width += bar.getWidth();
      }
    }
    return bounds;
  }

  @Override
  protected void updateLayoutState() {
    if (list.getLayoutOrientation() != JList.VERTICAL) {
      super.updateLayoutState();
      return;
    }

    // pasted from BasicListUI to provide min-height
    int fixedCellHeight = list.getFixedCellHeight();
    int fixedCellWidth = list.getFixedCellWidth();
    cellWidth = fixedCellWidth;
    if (fixedCellHeight != -1) {
      cellHeight = fixedCellHeight;
      cellHeights = null;
    }
    else {
      cellHeight = -1;
      cellHeights = new int[list.getModel().getSize()];
    }
    if ((fixedCellWidth == -1) || (fixedCellHeight == -1)) {
      ListModel<Object> dataModel = list.getModel();
      int dataModelSize = dataModel.getSize();
      ListCellRenderer<Object> renderer = list.getCellRenderer();
      if (renderer != null) {
        for (int index = 0; index < dataModelSize; index++) {
          Object value = dataModel.getElementAt(index);
          Component c = renderer.getListCellRendererComponent(list, value, index, false, false);
          rendererPane.add(c);
          Dimension cellSize = UIUtil.updateListRowHeight(c.getPreferredSize());
          if (fixedCellWidth == -1) {
            cellWidth = Math.max(cellSize.width, cellWidth);
          }
          if (fixedCellHeight == -1) {
            cellHeights[index] = cellSize.height;
          }
        }
      }
      else {
        if (cellWidth == -1) {
          cellWidth = 0;
        }
        if (cellHeights == null) {
          cellHeights = new int[dataModelSize];
        }
        for (int index = 0; index < dataModelSize; index++) {
          cellHeights[index] = 0;
        }
      }
    }
  }

  /** @noinspection MethodOverridesStaticMethodOfSuperclass, unused */
  public static ComponentUI createUI(JComponent list) {
    return new WideSelectionListUI();
  }
}
