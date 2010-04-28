/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Enumeration;


public class CheckboxTreeBase extends Tree {

  private final CheckPolicy myCheckPolicy;
  private static final CheckPolicy DEFAULT_POLICY = new CheckPolicy(true, true, false, true);

  public CheckboxTreeBase(final CheckboxTreeCellRendererBase cellRenderer, CheckedTreeNode root) {
    this(cellRenderer, root, DEFAULT_POLICY);
  }

  public CheckboxTreeBase(final CheckboxTreeCellRendererBase cellRenderer, CheckedTreeNode root, CheckPolicy checkPolicy) {
    myCheckPolicy = checkPolicy;

    setCellRenderer(cellRenderer);
    setRootVisible(false);
    setShowsRootHandles(true);
    setLineStyleAngled();
    TreeUtil.installActions(this);

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        int row = getRowForLocation(e.getX(), e.getY());
        if (row < 0) return;
        final Object o = getPathForRow(row).getLastPathComponent();
        if (!(o instanceof CheckedTreeNode)) return;
        Rectangle rowBounds = getRowBounds(row);
        cellRenderer.setBounds(rowBounds);
        Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();
        checkBounds.setLocation(rowBounds.getLocation());

        final CheckedTreeNode node = (CheckedTreeNode)o;
        if (checkBounds.contains(e.getPoint())) {
          if (node.isEnabled()) {
            toggleNode(node);
            setSelectionRow(row);
          }
          e.consume();
        }
        else if (e.getClickCount() > 1) {
          onDoubleClick(node);
        }
      }
    });

    addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (isToggleEvent(e)) {
          TreePath treePath = getLeadSelectionPath();
          if (treePath == null) return;
          final Object o = treePath.getLastPathComponent();
          if (!(o instanceof CheckedTreeNode)) return;
          CheckedTreeNode firstNode = (CheckedTreeNode)o;
          boolean checked = toggleNode(firstNode);

          TreePath[] selectionPaths = getSelectionPaths();
          for (int i = 0; selectionPaths != null && i < selectionPaths.length; i++) {
            final TreePath selectionPath = selectionPaths[i];
            final Object o1 = selectionPath.getLastPathComponent();
            if (!(o1 instanceof CheckedTreeNode)) continue;
            CheckedTreeNode node = (CheckedTreeNode)o1;
            checkNode(node, checked);
            ((DefaultTreeModel)getModel()).nodeChanged(node);
          }

          e.consume();
        }
      }
    });

    setSelectionRow(0);
    setModel(new DefaultTreeModel(root));
  }

  protected void onDoubleClick(final CheckedTreeNode node) {
  }

  protected boolean isToggleEvent(KeyEvent e) {
    return e.getKeyCode() == KeyEvent.VK_SPACE;
  }

  protected boolean toggleNode(CheckedTreeNode node) {
    boolean checked = !node.isChecked();
    checkNode(node, checked);

    // notify model listeners about model change
    final TreeModel model = getModel();
    model.valueForPathChanged(new TreePath(node.getPath()), node.getUserObject());

    return checked;
  }

  /**
   * Collect checked leaf nodes of the type {@code nodeType} and that are accepted by
   * {@code filter}
   *
   * @param nodeType the type of userobject to consider
   * @param filter   the filter (if null all nodes are accepted)
   * @param <T>      the type of the node
   * @return an array of collected nodes
   */
  @SuppressWarnings("unchecked")
  public <T> T[] getCheckedNodes(final Class<T> nodeType, @Nullable final NodeFilter<T> filter) {
    final ArrayList<T> nodes = new ArrayList<T>();
    final Object root = getModel().getRoot();
    if (!(root instanceof CheckedTreeNode)) {
      throw new IllegalStateException(
        "The root must be instance of the " + CheckedTreeNode.class.getName() + ": " + root.getClass().getName());
    }
    new Object() {
      @SuppressWarnings("unchecked")
      public void collect(CheckedTreeNode node) {
        if (node.isLeaf()) {
          Object userObject = node.getUserObject();
          if (node.isChecked() && userObject != null && nodeType.isAssignableFrom(userObject.getClass())) {
            final T value = (T)userObject;
            if (filter != null && !filter.accept(value)) return;
            nodes.add(value);
          }
        }
        else {
          for (int i = 0; i < node.getChildCount(); i++) {
            final TreeNode child = node.getChildAt(i);
            if (child instanceof CheckedTreeNode) {
              collect((CheckedTreeNode)child);
            }
          }
        }
      }
    }.collect((CheckedTreeNode)root);
    T[] result = (T[])Array.newInstance(nodeType, nodes.size());
    nodes.toArray(result);
    return result;
  }


  public int getToggleClickCount() {
    // to prevent node expanding/collapsing on checkbox toggling
    return -1;
  }

  protected void checkNode(CheckedTreeNode node, boolean checked) {
    adjustParentsAndChildren(node, checked);
    repaint();
  }

  protected void onNodeStateChanged(CheckedTreeNode node) {

  }

  protected void adjustParentsAndChildren(final CheckedTreeNode node, final boolean checked) {
    changeNodeState(node, checked);
    if (!checked) {
      if (myCheckPolicy.uncheckParentWithUncheckedChild) {
        TreeNode parent = node.getParent();
        while (parent != null) {
          if (parent instanceof CheckedTreeNode) {
            changeNodeState((CheckedTreeNode)parent, false);
          }
          parent = parent.getParent();
        }
      }
      if (myCheckPolicy.uncheckChildrenWithUncheckedParent) {
        uncheckChildren(node);
      }

    }
    else {
      if (myCheckPolicy.checkChildrenWithCheckedParent) {
        checkChildren(node);
      }

      if (myCheckPolicy.checkParentWithCheckedChild) {
        TreeNode parent = node.getParent();
        while (parent != null) {
          if (parent instanceof CheckedTreeNode) {
            changeNodeState((CheckedTreeNode)parent, true);
          }
          parent = parent.getParent();
        }
      }

    }
    repaint();
  }

  private void changeNodeState(final CheckedTreeNode node, final boolean checked) {
    if (node.isChecked() != checked) {
      node.setChecked(checked);
      onNodeStateChanged(node);
    }
  }

  private void uncheckChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final Object o = children.nextElement();
      if (!(o instanceof CheckedTreeNode)) continue;
      CheckedTreeNode child = (CheckedTreeNode)o;
      changeNodeState(child, false);
      uncheckChildren(child);
    }
  }

  private void checkChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final Object o = children.nextElement();
      if (!(o instanceof CheckedTreeNode)) continue;
      CheckedTreeNode child = (CheckedTreeNode)o;
      changeNodeState(child, true);
      checkChildren(child);
    }
  }

  protected void adjustParents(final CheckedTreeNode node, final boolean checked) {
    TreeNode parentNode = node.getParent();
    if (!(parentNode instanceof CheckedTreeNode)) return;
    CheckedTreeNode parent = (CheckedTreeNode)parentNode;

    if (!checked && isAllChildrenUnchecked(parent)) {
      changeNodeState(parent, false);
      adjustParents(parent, false);
    }
    else if (checked && isAllChildrenChecked(parent)) {
      changeNodeState(parent, true);
      adjustParents(parent, true);
    }
  }

  private static boolean isAllChildrenUnchecked(final CheckedTreeNode node) {
    for (int i = 0; i < node.getChildCount(); i++) {
      final TreeNode o = node.getChildAt(i);
      if ((o instanceof CheckedTreeNode) && ((CheckedTreeNode)o).isChecked()) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAllChildrenChecked(final CheckedTreeNode node) {
    for (int i = 0; i < node.getChildCount(); i++) {
      final TreeNode o = node.getChildAt(i);
      if ((o instanceof CheckedTreeNode) && !((CheckedTreeNode)o).isChecked()) {
        return false;
      }
    }
    return true;
  }

  public static abstract class CheckboxTreeCellRendererBase extends JPanel implements TreeCellRenderer {
    private final ColoredTreeCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;
    private final boolean myUsePartialStatusForParentNodes;

    public CheckboxTreeCellRendererBase(boolean opaque) {
      this(opaque, true);
    }

    public CheckboxTreeCellRendererBase(boolean opaque, final boolean usePartialStatusForParentNodes) {
      super(new BorderLayout());
      myUsePartialStatusForParentNodes = usePartialStatusForParentNodes;
      myCheckbox = new JCheckBox();
      myTextRenderer = new ColoredTreeCellRenderer() {
        public void customizeCellRenderer(JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
        }
      };
      myTextRenderer.setOpaque(opaque);
      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public CheckboxTreeCellRendererBase() {
      this(true);
    }

    public final Component getTreeCellRendererComponent(JTree tree,
                                                        Object value,
                                                        boolean selected,
                                                        boolean expanded,
                                                        boolean leaf,
                                                        int row,
                                                        boolean hasFocus) {
      invalidate();
      if (value instanceof CheckedTreeNode) {
        CheckedTreeNode node = (CheckedTreeNode)value;

        NodeState state = getNodeStatus(node);
        myCheckbox.setVisible(true);
        myCheckbox.setSelected(state != NodeState.CLEAR);
        myCheckbox.setEnabled(node.isEnabled() && state != NodeState.PARTIAL);

        myCheckbox.setBackground(null);
        setBackground(null);
      }
      else {
        myCheckbox.setVisible(false);
      }
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
      revalidate();


      return this;
    }

    private NodeState getNodeStatus(final CheckedTreeNode node) {
      final boolean checked = node.isChecked();
      if (node.getChildCount() == 0 || !myUsePartialStatusForParentNodes) return checked ? NodeState.FULL : NodeState.CLEAR;

      NodeState result = null;

      for (int i = 0; i < node.getChildCount(); i++) {
        TreeNode child = node.getChildAt(i);
        NodeState childStatus = child instanceof CheckedTreeNode? getNodeStatus((CheckedTreeNode)child) :
                checked? NodeState.FULL : NodeState.CLEAR;
        if (childStatus == NodeState.PARTIAL) return NodeState.PARTIAL;
        if (result == null) {
          result = childStatus;
        }
        else if (result != childStatus) {
          return NodeState.PARTIAL;
        }
      }

      return result == null ? NodeState.CLEAR : result;
    }

    /**
     * Should be implemented by concrete implementations.
     * This method is invoked only for customization of component.
     * All component attributes are cleared when this method is being invoked.
     * Note that in general case <code>value</code> is not an instance of CheckedTreeNode.
     */
    public void customizeRenderer(JTree tree,
                                  Object value,
                                  boolean selected,
                                  boolean expanded,
                                  boolean leaf,
                                  int row,
                                  boolean hasFocus) {
      if (value instanceof CheckedTreeNode) {
        customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
      }
    }

    /**
     * @deprecated
     * @see CheckboxTreeCellRendererBase#customizeRenderer(javax.swing.JTree, Object, boolean, boolean, boolean, int, boolean)
     */
    @Deprecated
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
    }

    public ColoredTreeCellRenderer getTextRenderer() {
      return myTextRenderer;
    }

    public JCheckBox getCheckbox() {
      return myCheckbox;
    }
  }


  public static enum NodeState {
    FULL, CLEAR, PARTIAL
  }

  public static class CheckPolicy {
    boolean checkChildrenWithCheckedParent;
    boolean uncheckChildrenWithUncheckedParent;
    boolean checkParentWithCheckedChild;
    boolean uncheckParentWithUncheckedChild;

    public CheckPolicy(final boolean checkChildrenWithCheckedParent,
                       final boolean uncheckChildrenWithUncheckedParent,
                       final boolean checkParentWithCheckedChild,
                       final boolean uncheckParentWithUncheckedChild) {
      this.checkChildrenWithCheckedParent = checkChildrenWithCheckedParent;
      this.uncheckChildrenWithUncheckedParent = uncheckChildrenWithUncheckedParent;
      this.checkParentWithCheckedChild = checkParentWithCheckedChild;
      this.uncheckParentWithUncheckedChild = uncheckParentWithUncheckedChild;
    }
  }
}
