// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd.aware;

import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.dnd.TransferableList;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class DnDAwareTree extends Tree implements DnDAware {
  public DnDAwareTree() {
    initDnD();
  }

  public DnDAwareTree(final TreeModel treemodel) {
    super(treemodel);
    initDnD();
  }

  public DnDAwareTree(final TreeNode root) {
    super(root);
    initDnD();
  }

  @Override
  public void processMouseEvent(final MouseEvent e) {
//todo [kirillk] to delegate this to DnDEnabler
    if (getToolTipText() == null && e.getID() == MouseEvent.MOUSE_ENTERED) return;
    super.processMouseEvent(e);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    if (SystemInfo.isMac && SwingUtilities.isRightMouseButton(e) && e.getID() == MouseEvent.MOUSE_DRAGGED) return;
    super.processMouseMotionEvent(e);
  }

  @Override
  public final boolean isOverSelection(final Point point) {
    final TreePath path = TreeUtil.getPathForLocation(this, point.x, point.y);
    if (path == null) return false;
    return isPathSelected(path);
  }

  @Override
  public void dropSelectionButUnderPoint(final Point point) {
    TreeUtil.dropSelectionButUnderPoint(this, point);
  }

  @Override
  @NotNull
  public final JComponent getComponent() {
    return this;
  }

  @NotNull
  public static Pair<Image, Point> getDragImage(@NotNull Tree dndAwareTree, @NotNull TreePath path, @NotNull Point dragOrigin) {
    int row = dndAwareTree.getRowForPath(path);
    Component comp = dndAwareTree.getCellRenderer().getTreeCellRendererComponent(dndAwareTree, path.getLastPathComponent(), false, true, true, row, false);
    return createDragImage(dndAwareTree, comp, dragOrigin, true);
  }

  @NotNull
  public static Pair<Image, Point> getDragImage(@NotNull Tree dndAwareTree, @NotNull @Nls String text, @Nullable Point dragOrigin) {
    return createDragImage(dndAwareTree, new JLabel(text), dragOrigin, false);
  }

  @NotNull
  private static Pair<Image, Point> createDragImage(@NotNull Tree tree,
                                                    @NotNull Component c,
                                                    @Nullable Point dragOrigin,
                                                    boolean adjustToPathUnderDragOrigin) {
    if (c instanceof JComponent) {
      ((JComponent)c).setOpaque(true);
    }

    c.setForeground(RenderingUtil.getForeground(tree));
    c.setBackground(RenderingUtil.getBackground(tree));
    c.setFont(tree.getFont());
    c.setSize(c.getPreferredSize());
    final BufferedImage image = UIUtil.createImage(c, c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = (Graphics2D)image.getGraphics();
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
    c.paint(g2);
    g2.dispose();

    Point point = new Point(image.getWidth(null) / 2, image.getHeight(null) / 2);

    if (adjustToPathUnderDragOrigin) {
      TreePath path = tree.getPathForLocation(dragOrigin.x, dragOrigin.y);
      if (path != null) {
        Rectangle bounds = tree.getPathBounds(path);
        point = new Point(dragOrigin.x - bounds.x, dragOrigin.y - bounds.y);
      }
    }

    return new Pair<>(image, point);
  }

  private void initDnD() {
    if (!GraphicsEnvironment.isHeadless()) {
      setDragEnabled(true);
      setTransferHandler(DEFAULT_TRANSFER_HANDLER);
    }
  }

  private static final TransferHandler DEFAULT_TRANSFER_HANDLER = new TransferHandler() {
    @Override
    protected Transferable createTransferable(JComponent component) {
      if (component instanceof JTree tree) {
        TreePath[] selection = tree.getSelectionPaths();
        if (selection != null && selection.length > 1) {
          return new TransferableList<>(selection) {
            @Override
            protected String toString(TreePath path) {
              return String.valueOf(path.getLastPathComponent());
            }
          };
        }
      }
      return null;
    }

    @Override
    public int getSourceActions(JComponent c) {
      return COPY_OR_MOVE;
    }
  };
}
