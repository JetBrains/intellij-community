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
package com.intellij.ui.treeStructure;

import com.intellij.Patches;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolTipHandler;
import com.intellij.ui.ToolTipHandlerFactory;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.EmptyTextHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.text.Position;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.dnd.Autoscroll;
import java.awt.event.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;

public class Tree extends JTree implements ComponentWithEmptyText, Autoscroll, Queryable {
  private EmptyTextHelper myEmptyTextHelper;
  private ToolTipHandler<Integer> myToolTipHandler;

  private AsyncProcessIcon myBusyIcon;
  private boolean myBusy;
  private Rectangle myLastVisibleRec;

  private Dimension myHoldSize;
  private MySelectionModel mySelectionModel = new MySelectionModel();

  public Tree() {
    initTree_();
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
    initTree_();
  }

  public Tree(TreeNode root) {
    super(root);
    initTree_();
  }

  private void initTree_() {
    myEmptyTextHelper = new EmptyTextHelper(this) {
      @Override
      protected boolean isEmpty() {
        return Tree.this.isEmpty();
      }
    };

    myToolTipHandler = ToolTipHandlerFactory.install(this);

    addMouseListener(new MyMouseListener());
    if (Patches.SUN_BUG_ID_4893787) {
      addFocusListener(new MyFocusListener());
    }

    setCellRenderer(new NodeRenderer());

    setSelectionModel(mySelectionModel);
  }

  @Override
  public void setUI(final TreeUI ui) {
    TreeUI actualUI = ui;
    if (SystemInfo.isMac && !isCustomUI() && UIUtil.isUnderAquaLookAndFeel() && !(ui instanceof UIUtil.MacTreeUI)) {
      actualUI = new UIUtil.MacTreeUI(isMacWideSelection());
    }

    super.setUI(actualUI);
  }

  public boolean isEmpty() {
    TreeModel model = getModel();
    if (model == null) return true;
    if (model.getRoot() == null) return true;
    return !isRootVisible() && model.getChildCount(model.getRoot()) == 0;
  }

  protected boolean isCustomUI() {
    return false;
  }

  protected boolean isMacWideSelection() {
    return true;
  }

  public String getEmptyText() {
    return myEmptyTextHelper.getEmptyText();
  }

  public void setEmptyText(String emptyText) {
    myEmptyTextHelper.setEmptyText(emptyText);
  }

  public void setEmptyText(String emptyText, SimpleTextAttributes attrs) {
    myEmptyTextHelper.setEmptyText(emptyText, attrs);
  }

  public void clearEmptyText() {
    myEmptyTextHelper.clearEmptyText();
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs) {
    myEmptyTextHelper.appendEmptyText(text, attrs);
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    myEmptyTextHelper.appendEmptyText(text, attrs, listener);
  }

  public ToolTipHandler<Integer> getToolTipHandler() {
    return myToolTipHandler;
  }

  @Override
  public void addNotify() {
    super.addNotify();

    updateBusy();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    if (myBusyIcon != null) {
      remove(myBusyIcon);
      Disposer.dispose(myBusyIcon);
      myBusyIcon = null;
    }
  }

  @Override
  public void doLayout() {
    super.doLayout();

    updateBusyIconLocation();
  }

  private void updateBusyIconLocation() {
    if (myBusyIcon != null) {
      final Rectangle rec = getVisibleRect();

      final Dimension iconSize = myBusyIcon.getPreferredSize();

      final Rectangle newBounds = new Rectangle(rec.x + rec.width - iconSize.width, rec.y, iconSize.width, iconSize.height);
      if (!newBounds.equals(myBusyIcon.getBounds())) {
        myBusyIcon.setBounds(newBounds);
        repaint();
      }
    }
  }

  @Override
  public void paint(Graphics g) {
    Rectangle clip = g.getClipBounds();
    final Rectangle visible = getVisibleRect();

    if (!AbstractTreeBuilder.isToPaintSelection(this)) {
      mySelectionModel.holdSelection();
    }

    try {
      super.paint(g);

      if (!visible.equals(myLastVisibleRec)) {
        updateBusyIconLocation();
      }

      myLastVisibleRec = visible;
    }
    finally {
      mySelectionModel.unholdSelection();
    }
  }

  public void setPaintBusy(boolean paintBusy) {
    if (myBusy == paintBusy) return;

    myBusy = paintBusy;
    updateBusy();
  }

  private void updateBusy() {
    if (myBusy) {
      if (myBusyIcon == null) {
        myBusyIcon = new AsyncProcessIcon(toString()).setUseMask(false);
        myBusyIcon.setOpaque(false);
        myBusyIcon.setPaintPassiveIcon(false);
        add(myBusyIcon);
        myBusyIcon.addMouseListener(new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (!UIUtil.isActionClick(e)) return;
            AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(Tree.this);
            if (builder != null) {
              builder.cancelUpdate();
            }
          }
        });
      }
    }

    if (myBusyIcon != null) {
      if (myBusy) {
        myBusyIcon.resume();
        myBusyIcon.setToolTipText("Update is in progress. Click to cancel");
      } else {
        myBusyIcon.suspend();
        myBusyIcon.setToolTipText(null);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myBusyIcon != null) {
              repaint();
            }
          }
        });
      }
      updateBusyIconLocation();
    }
  }

  protected boolean paintNodes() {
    return false;
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (paintNodes()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      paintNodeContent(g);
    }

    super.paintComponent(g);
    myEmptyTextHelper.paint(g);
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
            final int[] max = getMax(node, structure);
            rect = new Rectangle(parentRect.x, parentRect.y, Math.max((int) parentRect.getMaxX(), max[1]) - parentRect.x - 1,
                                 Math.max((int) parentRect.getMaxY(), max[0]) - parentRect.y - 1);
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
                TreePath prevPath = getPathForRow(nextRow - 1);
                if (prevPath != null) {
                  last = toPresentableNode(prevPath.getLastPathComponent());
                }
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

        if (last == null) continue;
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

  private int[] getMax(final PresentableNodeDescriptor node, final AbstractTreeStructure structure) {
    int x = 0;
    int y = 0;
    final Object[] children = structure.getChildElements(node);
    for (final Object child : children) {
      if (child instanceof PresentableNodeDescriptor) {
        final TreePath childPath = getPath((PresentableNodeDescriptor)child);
        if (childPath != null) {
          if (isExpanded(childPath)) {
            final int[] tmp = getMax((PresentableNodeDescriptor)child, structure);
            y = Math.max(y, tmp[0]);
            x = Math.max(x, tmp[1]);
          }

          final Rectangle r = getPathBounds(childPath);
          if (r != null) {
            y = Math.max(y, (int)r.getMaxY());
            x = Math.max(x, (int)r.getMaxX());
          }
        }
      }
    }

    return new int[]{y, x};
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

  private static class MySelectionModel extends DefaultTreeSelectionModel {

    private TreePath[] myHeldSelection;

    @Override
    protected void fireValueChanged(TreeSelectionEvent e) {
      if (myHeldSelection == null) {
        super.fireValueChanged(e);
      }
    }

    public void holdSelection() {
      myHeldSelection = getSelectionPaths();
      clearSelection();
    }

    public void unholdSelection() {
      if (myHeldSelection != null) {
        setSelectionPaths(myHeldSelection);
        myHeldSelection = null;
      }
    }
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
                if (selectionPath != null && selectionPath.equals(treepath)) return;
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

  public interface NodeFilter<T> {
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

  @Override
  public void reshape(int x, int y, int w, int h) {
    super.reshape(x, y, w, h);
  }

  public void setHoldSize(boolean hold) {
    if (hold && myHoldSize == null) {
      myHoldSize = getPreferredSize();
    } else if (!hold && myHoldSize != null) {
      myHoldSize = null;
      revalidate();
    }
  }

  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();

    if (myHoldSize != null) {
      size.width = Math.max(size.width, myHoldSize.width);
      size.height = Math.max(size.height, myHoldSize.height);      
    }

    return size;
  }
}