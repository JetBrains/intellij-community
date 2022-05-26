// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class TreeComboBox extends ComboBoxWithWidePopup {
  private static final int INDENT = UIUtil.getTreeLeftChildIndent();
  private TreeModel myTreeModel;
  private final boolean myShowRootNode;

  public TreeComboBox(@NotNull final TreeModel model) {
    this(model, true);
  }

  public TreeComboBox(@NotNull final TreeModel model, final boolean showRootNode) {
    this(model, showRootNode, null);
  }

  public TreeComboBox(@NotNull final TreeModel model, final boolean showRootNode, final String defaultText) {
    myTreeModel = model;
    myShowRootNode = showRootNode;
    setModel(new TreeModelWrapper(myTreeModel, showRootNode));
    setRenderer(new TreeListCellRenderer(this, showRootNode, defaultText));
  }

  public void setTreeModel(@NotNull final TreeModel model, final boolean showRootNode) {
    myTreeModel = model;
    setModel(new TreeModelWrapper(model, showRootNode));
  }

  public TreeModel getTreeModel() {
    return myTreeModel;
  }

  public JTree createFakeTree() {
    final JTree tree = new JTree(getTreeModel());
    tree.setRootVisible(myShowRootNode);
    return tree;
  }

  private static final class TreeListCellRenderer extends SimpleColoredRenderer implements ListCellRenderer {
    private static final Border SELECTION_PAINTER = (Border)UIManager.get("MenuItem.selectedBackgroundPainter");

    private boolean mySelected;
    private boolean myInList;
    private final JComboBox myComboBox;
    private boolean myChecked;
    private boolean myEditable;
    private final boolean myShowRootNode;
    private final @NlsContexts.Label String myDefaultText;

    private TreeListCellRenderer(@NotNull final JComboBox comboBox, final boolean showRootNode, @Nullable @NlsContexts.Label String defaultText) {
      myComboBox = comboBox;
      myShowRootNode = showRootNode;
      myDefaultText = defaultText;
      setOpaque(true);
    }

    private static Icon getValueIcon(final Object value, final int index) {
      if (value instanceof CustomPresentation) {
        return ((CustomPresentation)value).getIcon(index, 0);
      }
      if (value instanceof Iconable) {
        return ((Iconable)value).getIcon(0);
      }

      return null;
    }

    private TreeModelWrapper getTreeModelWrapper() {
      return (TreeModelWrapper)myComboBox.getModel();
    }

    @Override
    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      clear();

      myInList = index >= 0;
      if (index >= 0) {
        Object obj1 = myComboBox.getItemAt(index);
        myChecked = obj1 != null && obj1.equals(myComboBox.getSelectedItem());
      }
      else {
        myChecked = false;
      }

      int indent = 0;
      if (myInList) {
        final TreePath path = getTreeModelWrapper().getPathForRow(index);
        indent = (path.getPathCount() - 1 - (myShowRootNode ? 0 : 1)) * INDENT;
      }

      setIpad(new Insets(1, !myInList || myEditable ? 5 : 5 + indent, 1, 5));

      setIcon(getValueIcon(value, index));
      setIconOpaque(true);

      myEditable = myComboBox.isEditable();

      setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

      if (value instanceof CustomPresentation) {
        ((CustomPresentation)value).append(this, index);
      } else {
        if (value == null) {
          if (index == -1 && myDefaultText != null) {
            append(myDefaultText, SimpleTextAttributes.GRAY_ATTRIBUTES);
          } else {
            append("");
          }
        } else {
          //noinspection HardCodedStringLiteral
          append(value.toString());
        }
      }


      setSelected(isSelected);
      setFont(list.getFont());

      return this;
    }

    private void setSelected(final boolean selected) {
      mySelected = selected;
    }
  }

  private static final class TreeModelWrapper extends AbstractListModel implements ComboBoxModel {
    private final TreeModel myTreeModel;
    private Object mySelectedItem;
    private final boolean myShowRootNode;
    private final List<TreeNode> myTreeModelAsList = new ArrayList<>();

    private TreeModelWrapper(@NotNull final TreeModel treeModel, final boolean showRootNode) {
      myTreeModel = treeModel;
      myShowRootNode = showRootNode;
      accumulateChildren((TreeNode) treeModel.getRoot(), myTreeModelAsList, showRootNode);
    }

    public TreeModel getTreeModel() {
      return myTreeModel;
    }

    @Override
    public void setSelectedItem(final Object obj) {
      if (mySelectedItem != null && !mySelectedItem.equals(obj) || mySelectedItem == null && obj != null) {
        mySelectedItem = obj;
        fireContentsChanged(this, -1, -1);
      }
    }

    private static void accumulateChildren(@NotNull final TreeNode node, @NotNull final List<? super TreeNode> list, final boolean showRoot) {
      if (showRoot || node.getParent() != null) list.add(node);

      final int count = node.getChildCount();
      for (int i = 0; i < count; i++) {
        accumulateChildren(node.getChildAt(i), list, showRoot);
      }
    }

    private TreePath getPathForRow(final int row) {
      TreeNode node = myTreeModelAsList.get(row);
      final List<TreeNode> path = new ArrayList<>();
      while (node != null) {
        path.add(0, node);
        node = node.getParent();
      }

      return new TreePath(path.toArray(new TreeNode[0]));
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public int getSize() {
      int count = 0;
      Enumeration e = new PreorderEnumeration(myTreeModel);
      while (e.hasMoreElements()) {
        e.nextElement();
        count++;
      }

      return count - (myShowRootNode ? 0 : 1);
    }

    @Override
    public Object getElementAt(int index) {
      Enumeration e = new PreorderEnumeration(myTreeModel);
      if (!myShowRootNode) index++;
      for (int i = 0; i < index; i++) {
        e.nextElement();
      }

      return e.nextElement();
    }
  }

  private static class ChildrenEnumeration implements Enumeration {
    private final TreeModel myTreeModel;
    private final Object myNode;
    private int myIndex = -1;

    ChildrenEnumeration(@NotNull final TreeModel treeModel, @NotNull final Object node) {
      myTreeModel = treeModel;
      myNode = node;
    }

    @Override
    public boolean hasMoreElements() {
      return myIndex < myTreeModel.getChildCount(myNode) - 1;
    }

    @Override
    public Object nextElement() {
      return myTreeModel.getChild(myNode, ++myIndex);
    }
  }

  private static class PreorderEnumeration implements Enumeration {
    private final TreeModel myTreeModel;
    private final Stack<Enumeration> myStack;

    PreorderEnumeration(@NotNull final TreeModel treeModel) {
      myTreeModel = treeModel;
      myStack = new Stack<>();
      myStack.push(Collections.enumeration(Collections.singleton(treeModel.getRoot())));
    }

    @Override
    public boolean hasMoreElements() {
      return !myStack.empty() &&
              myStack.peek().hasMoreElements();
    }

    @Override
    public Object nextElement() {
      Enumeration e = myStack.peek();
      Object node = e.nextElement();
      if (!e.hasMoreElements()) {
        myStack.pop();
      }
      Enumeration children = new ChildrenEnumeration(myTreeModel, node);
      if (children.hasMoreElements()) {
        myStack.push(children);
      }
      return node;
    }
  }

  public interface CustomPresentation {
    void append(SimpleColoredComponent component, int index);
    Icon getIcon(int index, @Iconable.IconFlags int flags);
  }

}
