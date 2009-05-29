/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.ui.treeStructure;

import com.intellij.Patches;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.ui.TestableUi;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.text.Position;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.dnd.Autoscroll;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;

public class Tree extends JTree implements Autoscroll, TestableUi {

  public Tree() {
    patchTree();
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
    patchTree();
  }

  public Tree(TreeNode root) {
    super(root);
    patchTree();
  }

  private void patchTree() {
    addMouseListener(new MyMouseListener());
    if (Patches.SUN_BUG_ID_4893787) {
      addFocusListener(new MyFocusListener());
    }

    addFocusListener(new SelectionFixer());

    setCellRenderer(new NodeRenderer());
  }

  /**
   * Hack to prevent loosing multiple selection on Mac when clicking Ctrl+Left Mouse Button.
   * See faulty code at BasicTreeUI.selectPathForEvent():2245
   *
   * @param e
   */
  protected void processMouseEvent(MouseEvent e) {
    if (SystemInfo.isMac) {
      if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && e.getID() == MouseEvent.MOUSE_PRESSED) {
        int modifiers = (e.getModifiers() & ~(MouseEvent.CTRL_MASK | MouseEvent.BUTTON1_MASK)) | MouseEvent.BUTTON3_MASK;
        e = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), modifiers, e.getX(), e.getY(), e.getClickCount(), true,
                           MouseEvent.BUTTON3);
      }
    }
    super.processMouseEvent(e);
  }

  /**
   * Disable Sun's speedsearch
   */
  public TreePath getNextMatch(String prefix, int startingRow, Position.Bias bias) {
    return null;
  }

  private static final int AUTOSCROLL_MARGIN = 10;

  public Insets getAutoscrollInsets() {
    return new Insets(getLocation().y + AUTOSCROLL_MARGIN, 0, getParent().getHeight() - AUTOSCROLL_MARGIN, getWidth() - 1);
  }

  public void autoscroll(Point p) {
    int realrow = getClosestRowForLocation(p.x, p.y);
    if (getLocation().y + p.y <= AUTOSCROLL_MARGIN) {
      if (realrow >= 1) realrow--;
    }
    else {
      if (realrow < getRowCount() - 1) realrow++;
    }
    scrollRowToVisible(realrow);
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());

    paintNodeContent(g);

    super.paintComponent(g);
  }

  protected boolean highlightSingleNode() {
    return true;
  }

  private void paintNodeContent(Graphics g) {
    if (!(getUI() instanceof BasicTreeUI)) return;

    final AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(this);
    if (builder == null || builder.isDisposed()) return;

    GraphicsConfig config = new GraphicsConfig(g);
    config.setAntialiasing(true);

    final AbstractTreeStructure structure = builder.getTreeStructure();

    for (int eachRow = 0; eachRow < getRowCount(); eachRow++) {
      final TreePath path = getPathForRow(eachRow);
      PresentableNodeDescriptor node = toPresentableNode(path.getLastPathComponent());
      if (node == null) continue;

      if (!node.isContentHighlighted()) continue;

      if (highlightSingleNode()) {
        if (node.isContentHighlighted()) {
          final TreePath nodePath = getPath(node);

          Rectangle rect;

          final Rectangle parentRect = getPathBounds(nodePath);
          if (isExpanded(nodePath)) {
            Object[] kids = structure.getChildElements(node);
            if (kids.length > 0) {
              Object lastChild = kids[kids.length - 1];
              int[] max = getMax(kids, (int)parentRect.getMaxY(), (int)parentRect.getMaxX());
              if (lastChild instanceof PresentableNodeDescriptor) {
                while (isExpanded(getPath((PresentableNodeDescriptor) lastChild))) {
                  kids = structure.getChildElements(lastChild);
                  if (kids.length == 0) {
                    break;
                  }

                  lastChild = kids[kids.length - 1];
                  max = getMax(kids, max[0], max[1]);
                }
              }

              rect = new Rectangle(parentRect.x, parentRect.y, max[1] - parentRect.x + 1, max[0] - parentRect.y - 1);
            }
            else {
              rect = parentRect;
            }
          }
          else {
            rect = parentRect;
          }

          if (rect != null) {
            final Color highlightColor = node.getHighlightColor();
            g.setColor(highlightColor);
            g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);
            g.setColor(highlightColor.darker());
            g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);
          }
        }
      }
      else {
//todo: to investigate why it might happen under 1.6: http://www.productiveme.net:8080/browse/PM-217
        if (node.getParentDescriptor() == null) continue;

        final Object[] kids = structure.getChildElements(node);
        if (kids.length == 0) continue;

        PresentableNodeDescriptor first = null;
        PresentableNodeDescriptor last = null;
        int lastIndex = -1;
        for (int i = 0; i < kids.length; i++) {
          final Object kid = kids[i];
          if (kid instanceof PresentableNodeDescriptor) {
          PresentableNodeDescriptor eachKid = (PresentableNodeDescriptor) kid;
          if (!node.isHighlightableContentNode(eachKid)) continue;
          if (first == null) {
            first = eachKid;
          }
          last = eachKid;
          lastIndex = i;
          }
        }

        if (first == null || last == null) continue;
        Rectangle firstBounds = getPathBounds(getPath(first));

        if (isExpanded(getPath(last))) {
          if (lastIndex + 1 < kids.length) {
            final Object child = kids[lastIndex + 1];
            if (child instanceof PresentableNodeDescriptor) {
              PresentableNodeDescriptor nextKid = (PresentableNodeDescriptor) child;
              int nextRow = getRowForPath(getPath(nextKid));
              last = toPresentableNode(getPathForRow(nextRow - 1).getLastPathComponent());
            }
          }
          else {
            NodeDescriptor parentNode = node.getParentDescriptor();
            if (parentNode instanceof PresentableNodeDescriptor) {
              final PresentableNodeDescriptor ppd = (PresentableNodeDescriptor)parentNode;
              int nodeIndex = node.getIndex();
              if (nodeIndex + 1 < structure.getChildElements(ppd).length) {
                PresentableNodeDescriptor nextChild = ppd.getChildToHighlightAt(nodeIndex + 1);
                int nextRow = getRowForPath(getPath(nextChild));
                last = toPresentableNode(getPathForRow(nextRow - 1).getLastPathComponent());
              }
              else {
                int lastRow = getRowForPath(getPath(last));
                PresentableNodeDescriptor lastParent = last;
                boolean lastWasFound = false;
                for (int i = lastRow + 1; i < getRowCount(); i++) {
                  PresentableNodeDescriptor eachNode = toPresentableNode(getPathForRow(i).getLastPathComponent());
                  if (!node.isParentOf(eachNode)) {
                    last = lastParent;
                    lastWasFound = true;
                    break;
                  }
                  lastParent = eachNode;
                }
                if (!lastWasFound) {
                  last = toPresentableNode(getPathForRow(getRowCount() - 1).getLastPathComponent());
                }
              }
            }
          }
        }

        Rectangle lastBounds = getPathBounds(getPath(last));

        if (firstBounds == null || lastBounds == null) continue;

        Rectangle toPaint = new Rectangle(firstBounds.x, firstBounds.y, 0, (int)lastBounds.getMaxY() - firstBounds.y - 1);

        toPaint.width = getWidth() - toPaint.x - 4;

        final Color highlightColor = first.getHighlightColor();
        g.setColor(highlightColor);
        g.fillRoundRect(toPaint.x, toPaint.y, toPaint.width, toPaint.height, 4, 4);
        g.setColor(highlightColor.darker());
        g.drawRoundRect(toPaint.x, toPaint.y, toPaint.width, toPaint.height, 4, 4);
      }
    }

    config.restore();
  }

  private int[] getMax(final Object[] kids, final int y, final int x) {
    int maxY = 0;
    int maxX = 0;
    for (Object kid : kids) {
      if (kid instanceof PresentableNodeDescriptor) {
        final TreePath path = getPath((PresentableNodeDescriptor)kid);
        if (path != null) {
          final Rectangle r = getPathBounds(path);
          if (r != null) {
            maxY = Math.max(maxY, (int)r.getMaxY());
            maxX = Math.max(maxX, (int)r.getMaxX());
          }
        }
      }
    }

    return new int[]{Math.max(maxY, y), Math.max(maxX, x)};
  }

  @Nullable
  private static PresentableNodeDescriptor toPresentableNode(final Object pathComponent) {
    if (!(pathComponent instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode)pathComponent).getUserObject();
    if (!(userObject instanceof PresentableNodeDescriptor)) return null;
    return (PresentableNodeDescriptor)userObject;
  }

  public TreePath getPath(PresentableNodeDescriptor node) {
    final AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(this);
    final DefaultMutableTreeNode treeNode = builder.getNodeForElement(node);

    return treeNode != null ? new TreePath(treeNode.getPath()) : new TreePath(node);
  }

  private class MyMouseListener extends MouseAdapter {
    public void mousePressed(MouseEvent mouseevent) {
      if (!SwingUtilities.isLeftMouseButton(mouseevent) &&
          (SwingUtilities.isRightMouseButton(mouseevent) || SwingUtilities.isMiddleMouseButton(mouseevent))) {
        TreePath treepath = getPathForLocation(mouseevent.getX(), mouseevent.getY());
        if (treepath != null) {
          if (getSelectionModel().getSelectionMode() != TreeSelectionModel.SINGLE_TREE_SELECTION) {
            TreePath[] selectionPaths = getSelectionModel().getSelectionPaths();
            if (selectionPaths != null) {
              for (TreePath selectionPath : selectionPaths) {
                if (selectionPath == treepath) return;
              }
            }
          }
          getSelectionModel().setSelectionPath(treepath);
        }
      }
    }
  }

  /**
   * This is patch for 4893787 SUN bug. The problem is that the BasicTreeUI.FocusHandler repaints
   * only lead selection index on focus changes. It's a problem with multiple selected nodes.
   */
  private class MyFocusListener extends FocusAdapter {
    private void focusChanges() {
      TreePath[] paths = getSelectionPaths();
      if (paths != null) {
        TreeUI ui = getUI();
        for (int i = paths.length - 1; i >= 0; i--) {
          Rectangle bounds = ui.getPathBounds(Tree.this, paths[i]);
          if (bounds != null) {
            repaint(bounds);
          }
        }
      }
    }

    public void focusGained(FocusEvent e) {
      focusChanges();
    }

    public void focusLost(FocusEvent e) {
      focusChanges();
    }
  }

  private class SelectionFixer extends FocusAdapter {
    public void focusGained(final FocusEvent e) {
      final TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length == 0) {
        for (int eachRow = 0; eachRow < getRowCount(); eachRow++) {
          final TreePath path = getPathForRow(eachRow);
          if (path != null && isVisible(path)) {
            setSelectionPath(path);
            break;
          }
        }
      }
    }
  }

  public final void setLineStyleAngled() {
    UIUtil.setLineStyleAngled(this);
  }

  public <T> T[] getSelectedNodes(Class<T> nodeType, @Nullable NodeFilter<T> filter) {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return (T[])Array.newInstance(nodeType, 0);

    ArrayList<T> nodes = new ArrayList<T>();
    for (int i = 0; i < paths.length; i++) {
      Object last = paths[i].getLastPathComponent();
      if (nodeType.isAssignableFrom(last.getClass())) {
        if (filter != null && !filter.accept((T)last)) continue;
        nodes.add((T)last);
      }
    }
    T[] result = (T[])Array.newInstance(nodeType, nodes.size());
    nodes.toArray(result);
    return result;
  }

  public static interface NodeFilter<T> {
    boolean accept(T node);
  }

  public void putInfo(Map<String, String> info) {
    final TreePath[] selection = getSelectionPaths();
    if (selection == null) return;

    StringBuffer nodesText = new StringBuffer();

    for (TreePath eachPath : selection) {
      final Object eachNode = eachPath.getLastPathComponent();
      final Component c =
        getCellRenderer().getTreeCellRendererComponent(this, eachNode, false, false, false, getRowForPath(eachPath), false);

      if (c != null) {
        if (nodesText.length() > 0) {
          nodesText.append(";");
        }
        nodesText.append(c.toString());
      }
    }

    if (nodesText.length() > 0) {
      info.put("selectedNodes", nodesText.toString());
    }
  }
}