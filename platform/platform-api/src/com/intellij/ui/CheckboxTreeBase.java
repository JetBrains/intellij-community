/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import java.awt.*;

public class CheckboxTreeBase extends Tree {
  private final CheckboxTreeHelper myHelper;
  private final EventDispatcher<CheckboxTreeListener> myEventDispatcher = EventDispatcher.create(CheckboxTreeListener.class);

  public CheckboxTreeBase() {
    this(new CheckboxTreeCellRendererBase(), null);
  }

  public CheckboxTreeBase(final CheckboxTreeCellRendererBase cellRenderer, CheckedTreeNode root) {
    this(cellRenderer, root, CheckboxTreeHelper.DEFAULT_POLICY);
  }

  public CheckboxTreeBase(CheckboxTreeCellRendererBase cellRenderer, @Nullable CheckedTreeNode root, CheckPolicy checkPolicy) {
    myHelper = new CheckboxTreeHelper(checkPolicy, myEventDispatcher);
    if (root != null) {
      // override default model ("colors", etc.) ASAP to avoid CCE in renderers
      setModel(new DefaultTreeModel(root));
      setSelectionRow(0);
    }
    myEventDispatcher.addListener(new CheckboxTreeListener() {
      @Override
      public void mouseDoubleClicked(@NotNull CheckedTreeNode node) {
        onDoubleClick(node);
      }

      @Override
      public void nodeStateChanged(@NotNull CheckedTreeNode node) {
        CheckboxTreeBase.this.onNodeStateChanged(node);
      }

      @Override
      public void beforeNodeStateChanged(@NotNull CheckedTreeNode node) {
        CheckboxTreeBase.this.nodeStateWillChange(node);
      }
    });
    myHelper.initTree(this, this, cellRenderer);
  }

  @Deprecated
  public void installRenderer(final CheckboxTreeCellRendererBase cellRenderer) {
    setCellRenderer(cellRenderer);
  }

  /**
   * @deprecated use {@link #setNodeState} to change node state or subscribe to {@link #addCheckboxTreeListener} to get notifications about state changes
   */
  @Deprecated
  protected boolean toggleNode(CheckedTreeNode node) {
    setNodeState(node, !node.isChecked());
    return node.isChecked();
  }

  /**
   * @deprecated use {@link #setNodeState} to change node state or subscribe to {@link #addCheckboxTreeListener} to get notifications about state changes
   */
  @Deprecated
  protected void checkNode(CheckedTreeNode node, boolean checked) {
    setNodeState(node, checked);
  }

  public void setNodeState(@NotNull CheckedTreeNode node, boolean checked) {
    myHelper.setNodeState(this, node, checked);
  }

  public void addCheckboxTreeListener(@NotNull CheckboxTreeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  protected void onDoubleClick(final CheckedTreeNode node) {
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
  public <T> T[] getCheckedNodes(final Class<T> nodeType, @Nullable final NodeFilter<T> filter) {
    return CheckboxTreeHelper.getCheckedNodes(nodeType, filter, getModel());
  }


  public int getToggleClickCount() {
    // to prevent node expanding/collapsing on checkbox toggling
    return -1;
  }

  protected void onNodeStateChanged(CheckedTreeNode node) {
  }

  protected void nodeStateWillChange(CheckedTreeNode node) {
  }

  @Deprecated
  protected void adjustParents(final CheckedTreeNode node, final boolean checked) {
  }

  public static class CheckboxTreeCellRendererBase extends JPanel implements TreeCellRenderer {
    private final ColoredTreeCellRenderer myTextRenderer;
    public final ThreeStateCheckBox myCheckbox;
    private final boolean myUsePartialStatusForParentNodes;
    protected boolean myIgnoreInheritance;

    public CheckboxTreeCellRendererBase(boolean opaque) {
      this(opaque, true);
    }

    public CheckboxTreeCellRendererBase(boolean opaque, final boolean usePartialStatusForParentNodes) {
      super(new BorderLayout());
      myUsePartialStatusForParentNodes = usePartialStatusForParentNodes;
      myCheckbox = new ThreeStateCheckBox();
      myCheckbox.setSelected(false);
      myCheckbox.setThirdStateEnabled(false);
      myTextRenderer = new ColoredTreeCellRenderer() {
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) { }
      };
      myTextRenderer.setOpaque(opaque);
      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public CheckboxTreeCellRendererBase() {
      this(true);
    }

    public final Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      invalidate();
      if (value instanceof CheckedTreeNode) {
        CheckedTreeNode node = (CheckedTreeNode)value;

        NodeState state = getNodeStatus(node);
        myCheckbox.setVisible(true);
        myCheckbox.setSelected(state != NodeState.CLEAR);
        myCheckbox.setEnabled(node.isEnabled() && state != NodeState.PARTIAL);
        myCheckbox.setOpaque(false);
        myCheckbox.setBackground(null);
        setBackground(null);

        if (UIUtil.isUnderWin10LookAndFeel()) {
          Object hoverValue = getClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY);
          myCheckbox.getModel().setRollover(hoverValue == value);

          Object pressedValue = getClientProperty(UIUtil.CHECKBOX_PRESSED_PROPERTY);
          myCheckbox.getModel().setPressed(pressedValue == value);
        }
      }
      else {
        myCheckbox.setVisible(false);
      }
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

      if (UIUtil.isUnderGTKLookAndFeel()) {
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }
      else if (UIUtil.isUnderNimbusLookAndFeel()) {
        UIUtil.changeBackGround(this, UIUtil.TRANSPARENT_COLOR);
      }
      customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
      revalidate();

      return this;
    }

    private NodeState getNodeStatus(final CheckedTreeNode node) {
      if (myIgnoreInheritance) return node.isChecked() ? NodeState.FULL : NodeState.CLEAR;
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
     * Note that in general case {@code value} is not an instance of CheckedTreeNode.
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
     * @see CheckboxTreeCellRendererBase#customizeRenderer(JTree, Object, boolean, boolean, boolean, int, boolean)
     * @deprecated
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


  public enum NodeState {
    FULL, CLEAR, PARTIAL
  }

  public static class CheckPolicy {
    final boolean checkChildrenWithCheckedParent;
    final boolean uncheckChildrenWithUncheckedParent;
    final boolean checkParentWithCheckedChild;
    final boolean uncheckParentWithUncheckedChild;

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
