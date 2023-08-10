// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import java.awt.*;

import static com.intellij.util.ui.ThreeStateCheckBox.State;

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
  public <T> T[] getCheckedNodes(final Class<? extends T> nodeType, @Nullable final NodeFilter<? super T> filter) {
    return CheckboxTreeHelper.getCheckedNodes(nodeType, filter, getModel());
  }


  @Override
  public int getToggleClickCount() {
    // to prevent node expanding/collapsing on checkbox toggling
    return -1;
  }

  protected void onNodeStateChanged(CheckedTreeNode node) {
  }

  protected void nodeStateWillChange(CheckedTreeNode node) {
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
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) { }
      };
      myTextRenderer.setOpaque(opaque);
      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public CheckboxTreeCellRendererBase() {
      this(true);
    }

    @Override
    public final Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      invalidate();
      if (value instanceof CheckedTreeNode node) {

        State state = getNodeStatus(node);
        myCheckbox.setVisible(true);
        myCheckbox.setEnabled(node.isEnabled());
        myCheckbox.setSelected(state != State.NOT_SELECTED);
        myCheckbox.setState(state);
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

      customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
      revalidate();

      return this;
    }

    private State getNodeStatus(final CheckedTreeNode node) {
      State ownState = node.isChecked() ? State.SELECTED : State.NOT_SELECTED;
      if (myIgnoreInheritance || node.getChildCount() == 0 || !myUsePartialStatusForParentNodes) {
        return ownState;
      }

      State result = null;
      for (int i = 0; i < node.getChildCount(); i++) {
        TreeNode child = node.getChildAt(i);
        State childStatus = child instanceof CheckedTreeNode? getNodeStatus((CheckedTreeNode)child) : ownState;
        if (childStatus == State.DONT_CARE) return State.DONT_CARE;
        if (result == null) {
          result = childStatus;
        }
        else if (result != childStatus) {
          return State.DONT_CARE;
        }
      }
      return result == null ? ownState : result;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleContextDelegateWithContextMenu(super.getAccessibleContext()) {
          @Override
          protected Container getDelegateParent() {
            return getParent();
          }

          @Override
          protected void doShowContextMenu() {
            ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true);
          }

          @Override
          public String getAccessibleName() {
            return AccessibleContextUtil.combineAccessibleStrings(
              myTextRenderer.getAccessibleContext().getAccessibleName(),
              UIBundle.message(myCheckbox.isSelected() ? "checkbox.tree.accessible.name.checked" : "checkbox.tree.accessible.name.not.checked"));
          }
        };
      }
      return accessibleContext;
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
    }

    public ColoredTreeCellRenderer getTextRenderer() {
      return myTextRenderer;
    }

    public JCheckBox getCheckbox() {
      return myCheckbox;
    }
  }

  /**
   * @see State
   * @deprecated Don't use this enum. Left for API compatibility.
   */
  @Deprecated(forRemoval = true)
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
