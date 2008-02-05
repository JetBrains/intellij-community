package com.intellij.ide.dnd.aware;

import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.dnd.DnDEnabler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.TreeUtils;
import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class DnDAwareTree extends Tree implements DnDAware {
  private DnDEnabler myDnDEnabler;

  public DnDAwareTree() {
  }

  public DnDAwareTree(final TreeModel treemodel) {
    super(treemodel);
  }

  public DnDAwareTree(final TreeNode root) {
    super(root);
  }

  public void enableDnd(Disposable parent) {
    myDnDEnabler = new DnDEnabler(this, parent);
    Disposer.register(parent, myDnDEnabler);
  }

  public void processMouseEvent(final MouseEvent e) {
//todo [kirillk] to delegate this to DnDEnabler
    if (getToolTipText() == null && e.getID() == MouseEvent.MOUSE_ENTERED) return;
    super.processMouseEvent(e);
  }

  public final boolean isOverSelection(final Point point) {
    final TreePath path = getPathForLocation(point.x, point.y);
    if (path == null) return false;
    return isPathSelected(path);
  }

  public void dropSelectionButUnderPoint(final Point point) {
    TreeUtils.dropSelectionButUnderPoint(this, point);
  }

  @NotNull
  public final JComponent getComponent() {
    return this;
  }

  public static Pair<Image, Point> getDragImage(DnDAwareTree tree, TreePath path, Point dragOrigin) {
    int row = tree.getRowForPath(path);
    Component comp = tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), false, true, true, row, false);
    return createDragImage(tree, comp, dragOrigin, true);
  }

  public static Pair<Image, Point> getDragImage(DnDAwareTree tree, final String text, Point dragOrigin) {
    return createDragImage(tree, new JLabel(text), dragOrigin, false);
  }

  private static Pair<Image, Point> createDragImage(final DnDAwareTree tree, final Component c, Point dragOrigin, boolean adjustToPathUnderDragOrigin) {
    if (c instanceof JComponent) {
      ((JComponent)c).setOpaque(true);
    }

    c.setForeground(tree.getForeground());
    c.setBackground(tree.getBackground());
    c.setFont(tree.getFont());
    c.setSize(c.getPreferredSize());
    final BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = (Graphics2D)image.getGraphics();
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
    c.paint(g2);
    g2.dispose();

    Point point = new Point(-image.getWidth(null) / 2, -image.getHeight(null) / 2);

    if (adjustToPathUnderDragOrigin) {
      TreePath path = tree.getPathForLocation(dragOrigin.x, dragOrigin.y);
      if (path != null) {
        Rectangle bounds = tree.getPathBounds(path);
        point = new Point(bounds.x - dragOrigin.x, bounds.y - dragOrigin.y);
      }
    }

    return new Pair<Image, Point>(image, point);
  }

  
}
