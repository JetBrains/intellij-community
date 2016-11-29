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
package com.intellij.openapi.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
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

/**
 * User: spLeaner
 */
public class TreeComboBox extends ComboBoxWithWidePopup {
  private static final int INDENT = UIUtil.getTreeLeftChildIndent();
  private TreeModel myTreeModel;
  private final String myDefaultText;
  private final boolean myShowRootNode;

  public TreeComboBox(@NotNull final TreeModel model) {
    this(model, true);
  }

  public TreeComboBox(@NotNull final TreeModel model, final boolean showRootNode) {
    this(model, showRootNode, null);
  }

  public TreeComboBox(@NotNull final TreeModel model, final boolean showRootNode, final String defaultText) {
    myTreeModel = model;
    myDefaultText = defaultText;
    myShowRootNode = showRootNode;
    setModel(new TreeModelWrapper(myTreeModel, showRootNode));
    setRenderer(new TreeListCellRenderer(this, showRootNode, defaultText));
    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) setMaximumRowCount(25);
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

  private static class TreeListCellRenderer extends SimpleColoredRenderer implements ListCellRenderer {
    private static final Border SELECTION_PAINTER = (Border)UIManager.get("MenuItem.selectedBackgroundPainter");

    private boolean mySelected;
    private boolean myInList;
    private final JComboBox myComboBox;
    private boolean myChecked;
    private boolean myEditable;
    private final boolean myUnderAquaLookAndFeel;
    private final boolean myShowRootNode;
    private final String myDefaultText;

    private TreeListCellRenderer(@NotNull final JComboBox comboBox, final boolean showRootNode, @Nullable final String defaultText) {
      myComboBox = comboBox;
      myShowRootNode = showRootNode;
      myDefaultText = defaultText;
      myUnderAquaLookAndFeel = UIUtil.isUnderAquaLookAndFeel();
      setOpaque(!myUnderAquaLookAndFeel);
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
        indent = path == null ? 0 : (path.getPathCount() - 1 - (myShowRootNode ? 0 : 1)) *
                                    (UIUtil.isUnderAquaLookAndFeel() ? 2 : 1) * INDENT;
      }

      setIpad(new Insets(1, !myInList || myEditable ? myUnderAquaLookAndFeel ? 0 : 5 : (myUnderAquaLookAndFeel ? 23 : 5) + indent, 1, 5));

      setIcon(getValueIcon(value, index));
      setIconOpaque(!myUnderAquaLookAndFeel);

      myEditable = myComboBox.isEditable();

      setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      if (!myUnderAquaLookAndFeel) setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

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

    @Override
    protected boolean shouldPaintBackground() {
      return !myUnderAquaLookAndFeel;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (myUnderAquaLookAndFeel) {
        if (mySelected) {
          SELECTION_PAINTER.paintBorder(this, g, 0, 0, getWidth(), getHeight());
        }

        if (SystemInfo.isMac && myChecked && !myEditable) {
          int i = getHeight() - 4;
          g.setColor(getForeground());
          g.drawString("\u2713", 6, i);
        }
      }

      super.paintComponent(g);
    }
  }

  private static class TreeModelWrapper extends AbstractListModel implements ComboBoxModel {
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

    private static void accumulateChildren(@NotNull final TreeNode node, @NotNull final List<TreeNode> list, final boolean showRoot) {
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

      return new TreePath(path.toArray(new TreeNode[path.size()]));
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    public int getSize() {
      int count = 0;
      Enumeration e = new PreorderEnumeration(myTreeModel);
      while (e.hasMoreElements()) {
        e.nextElement();
        count++;
      }

      return count - (myShowRootNode ? 0 : 1);
    }

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

    public ChildrenEnumeration(@NotNull final TreeModel treeModel, @NotNull final Object node) {
      myTreeModel = treeModel;
      myNode = node;
    }

    public boolean hasMoreElements() {
      return myIndex < myTreeModel.getChildCount(myNode) - 1;
    }

    public Object nextElement() {
      return myTreeModel.getChild(myNode, ++myIndex);
    }
  }

  private static class PreorderEnumeration implements Enumeration {
    private final TreeModel myTreeModel;
    private final Stack<Enumeration> myStack;

    public PreorderEnumeration(@NotNull final TreeModel treeModel) {
      myTreeModel = treeModel;
      myStack = new Stack<>();
      myStack.push(Collections.enumeration(Collections.singleton(treeModel.getRoot())));
    }

    public boolean hasMoreElements() {
      return !myStack.empty() &&
              myStack.peek().hasMoreElements();
    }

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
