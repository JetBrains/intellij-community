// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure.treetable;

import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;

import static com.intellij.ui.render.RenderingUtil.FOCUSABLE_SIBLING;

/**
 * author: lesya
 */
public class TreeTableTree extends Tree {
  private Border myBorder;
  private final TreeTable myTreeTable;
  private int myVisibleRow;
  private boolean myCellFocused;


  public TreeTableTree(TreeModel model, TreeTable treeTable) {
    super(model);
    myTreeTable = treeTable;
    setCellRenderer(getCellRenderer());
    putClientProperty(FOCUSABLE_SIBLING, treeTable);
  }

  public TreeTable getTreeTable() {
    return myTreeTable;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setBorder(null);
    TreeCellRenderer tcr = super.getCellRenderer();
    if (tcr instanceof DefaultTreeCellRenderer dtcr) {
      dtcr.setTextSelectionColor(UIUtil.getTableSelectionForeground(true));
      dtcr.setBackgroundSelectionColor(UIUtil.getTableSelectionBackground(true));
    }
  }

  @Override
  protected final boolean isWideSelection() {
    return StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF();
  }

  @Override
  public void setRowHeight(int rowHeight) {
    if (rowHeight > 0) {
      super.setRowHeight(rowHeight);
      if (myTreeTable != null && myTreeTable.getRowHeight() != rowHeight) {
        myTreeTable.setRowHeight(getRowHeight());
      }
    }
  }

  @Override
  public void setBounds(int x, int y, int w, int h) {
    super.setBounds(x, 0, w, myTreeTable.getHeight());
  }

  @Override
  public void paint(Graphics g) {
    putClientProperty("JTree.lineStyle", "None");
    Graphics g1 = g.create();
    g1.translate(0, -myVisibleRow * getRowHeight());
    super.paint(g1);
    g1.dispose();
    if (myBorder != null) {
      myBorder.paintBorder(this, g, 0, 0, myTreeTable.getWidth(), getRowHeight());
    }
  }

  @Override
  public void setBorder(Border border) {
    super.setBorder(border);
    myBorder = border;
  }

  public void setTreeTableTreeBorder(Border border) {
    myBorder = border;
  }

  public void setVisibleRow(int row) {
    myVisibleRow = row;
    final Rectangle rowBounds = getRowBounds(myVisibleRow);
    final int indent = rowBounds.x - getVisibleRect().x - getTreeColumnOffsetX();
    setPreferredSize(new Dimension(getRowBounds(myVisibleRow).width + indent, getPreferredSize().height));
  }

  public void _processKeyEvent(KeyEvent e) {
    super.processKeyEvent(e);
  }

  public void setCellFocused(boolean focused) {
    myCellFocused = focused;
  }

  @Override
  public void setCellRenderer(final TreeCellRenderer renderer) {
    TreeTableTreeCellRendererWrapper wrapper = renderer instanceof TreeTableTreeCellRendererWrapper
                                               ? ((TreeTableTreeCellRendererWrapper)renderer)
                                               : new TreeTableTreeCellRendererWrapper(renderer);
    super.setCellRenderer(wrapper);
  }

  public TreeCellRenderer getOriginalCellRenderer() {
    TreeCellRenderer renderer = super.getCellRenderer();
    if (renderer instanceof TreeTableTreeCellRendererWrapper) {
      return ((TreeTableTreeCellRendererWrapper)renderer).myDelegate;
    }
    return renderer;
  }

  @Override
  public @Nullable Rectangle getPathBounds(TreePath path) {
    Rectangle bounds = super.getPathBounds(path);
    if (bounds == null) {
      return null;
    }
    bounds.x += getTreeColumnOffsetX();
    return bounds;
  }

  protected int getTreeColumnOffsetX() {
    int offsetX = 0;
    for (int i = 0; i < myTreeTable.getColumnCount(); i++) {
      if (myTreeTable.isTreeColumn(i)) {
        break;
      }
      offsetX += myTreeTable.getColumnModel().getColumn(i).getWidth();
    }
    return offsetX;
  }

  private class TreeTableTreeCellRendererWrapper implements TreeCellRenderer {
    private final TreeCellRenderer myDelegate;

    TreeTableTreeCellRendererWrapper(@NotNull TreeCellRenderer delegate) {
      myDelegate = delegate;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                  boolean selected, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {
      return myDelegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, myCellFocused);
    }
  }
}
