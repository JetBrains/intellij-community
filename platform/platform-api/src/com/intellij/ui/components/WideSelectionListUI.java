// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.ui.BackgroundSupplier;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.hover.ListHoverListener;
import com.intellij.ui.list.ListCellBackgroundSupplier;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashMap;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.ui.paint.RectanglePainter.DRAW;

/**
 * @noinspection ALL
 */
public final class WideSelectionListUI extends BasicListUI {
  private static final Logger LOG = Logger.getInstance(WideSelectionListUI.class);
  private Rectangle myPaintBounds;
  private HashMap<@NotNull Integer, @NotNull Dimension> preferredSizeCache = new HashMap<>();

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
    boolean selected = selectionModel.isSelectedIndex(row);
    Rectangle paintBounds = myPaintBounds;
    if (paintBounds != null) {
      boolean focused = row == leadSelectionIndex && (!list.isFocusable() || list.hasFocus());
      Object value = model.getElementAt(row);
      Color background = getBackground(list, value, row, selected);
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
    try {
      super.paintCell(g, row, rowBounds, renderer, model, selectionModel, leadSelectionIndex);
    }
    catch (IndexOutOfBoundsException exception) {
      LOG.error("model asynchronously modified: " + model.getClass() + " in " + list, exception);
    }
    if (!isMac && g instanceof Graphics2D && row == leadSelectionIndex && list.hasFocus()) {
      int x = rowBounds.x;
      int width = rowBounds.width;
      if (JList.VERTICAL == list.getLayoutOrientation()) {
        x = 0;
        width = list.getWidth();
        JViewport viewport = ComponentUtil.getViewport(list);
        if (viewport != null) {
          x = -list.getX();
          width = viewport.getWidth();
        }
      }
      if (!selected) {
        g.setColor(UIUtil.getListSelectionBackground(true));
        g.setClip(x, rowBounds.y, width, rowBounds.height);
        DRAW.paint((Graphics2D)g, x, rowBounds.y, width, rowBounds.height, 0);
      }
      else if (isLeadSelectionNeeded(list, row)) {
        g.setColor(UIUtil.getListBackground());
        g.setClip(x, rowBounds.y, width, rowBounds.height);
        DRAW.paint((Graphics2D)g, x + 1, rowBounds.y + 1, width - 2, rowBounds.height - 2, 0);
      }
    }
  }

  private static boolean isLeadSelectionNeeded(@NotNull JList list, int row) {
    return list.getMinSelectionIndex() < list.getMaxSelectionIndex() && list.isSelectedIndex(row - 1) && list.isSelectedIndex(row + 1);
  }

  @Nullable
  private static Color getBackground(@NotNull JList<Object> list, @Nullable Object value, int row, boolean selected) {
    // to be consistent with com.intellij.ui.tree.ui.DefaultTreeUI#getBackground
    if (selected) {
      return RenderingUtil.getSelectionBackground(list);
    }
    if (row == ListHoverListener.getHoveredIndex(list)) {
      Color background = RenderingUtil.getHoverBackground(list);
      if (background != null) return background;
    }
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
      ListCellBackgroundSupplier<Object> supplier = (ListCellBackgroundSupplier<Object>)list;
      Color background = supplier.getCellBackground(value, row);
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
        JScrollPane pane = ComponentUtil.getScrollPane(list);
        JScrollBar bar = pane == null ? null : pane.getVerticalScrollBar();
        if (bar != null && !bar.isOpaque()) bounds.width += bar.getWidth();
      }
    }
    return bounds;
  }

  @Override
  public void uninstallUI(JComponent c) {
    preferredSizeCache.clear();
    super.uninstallUI(c);
  }

  @Override
  protected ListDataListener createListDataListener() {
    ListDataListener superListener = super.createListDataListener();

    return new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        superListener.intervalAdded(e);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        removeFromCache(e);
        superListener.intervalRemoved(e);
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        removeFromCache(e);
        superListener.contentsChanged(e);
      }
    };
  }

  private void removeFromCache(ListDataEvent e) {
    for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
      preferredSizeCache.remove(i);
    }
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
        boolean immutableRenderer = list.getClientProperty(JBList.IMMUTABLE_MODEL_AND_RENDERER) == Boolean.TRUE;
        for (int index = 0; index < dataModelSize; index++) {
          Dimension cellSize = getItemPreferredSize(index, dataModel, renderer, immutableRenderer);
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

  private @NotNull Dimension getItemPreferredSize(int index,
                                                  @NotNull ListModel<Object> dataModel,
                                                  @NotNull ListCellRenderer<Object> renderer,
                                                  boolean immutableRenderer) {
    if (immutableRenderer) {
      Dimension result = preferredSizeCache.get(index);
      if (result != null) {
        return result;
      }
    }

    Object value = dataModel.getElementAt(index);
    Component c = renderer.getListCellRendererComponent(list, value, index, false, false);
    rendererPane.add(c);
    var result = c.getPreferredSize();
    if (ClientProperty.get(c, JBList.IGNORE_LIST_ROW_HEIGHT) == null) {
      result = UIUtil.updateListRowHeight(result);
    }

    if (immutableRenderer) {
      preferredSizeCache.put(index, result);
    }
    return result;
  }

  @Override
  protected FocusListener createFocusListener() {
    var superFocusListener = super.createFocusListener();

    // Support selected items background while changing focus
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (list.getSelectionMode() == ListSelectionModel.SINGLE_SELECTION) {
          superFocusListener.focusGained(e);
        } else {
          list.repaint();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (list.getSelectionMode() == ListSelectionModel.SINGLE_SELECTION) {
          superFocusListener.focusLost(e);
        } else {
          list.repaint();
        }
      }
    };
  }

  /** @noinspection MethodOverridesStaticMethodOfSuperclass, unused */
  public static ComponentUI createUI(JComponent list) {
    return new WideSelectionListUI();
  }
}
