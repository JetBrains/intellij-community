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
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.Enumeration;

/**
 * User: spLeaner
 */
public class TreeComboBox extends JComboBox {
  final static int INDENT = UIManager.getInt("Tree.leftChildIndent");
  private TreeModel myTreeModel;

  public TreeComboBox(@NotNull final TreeModel model) {
    myTreeModel = model;
    setModel(new TreeModelWrapper(myTreeModel));
    setRenderer(new TreeListCellRenderer(this, model));
    if (SystemInfo.isMac) setMaximumRowCount(25);
  }

  public TreeModel getTreeModel() {
    return myTreeModel;
  }

  private static class TreeListCellRenderer extends JLabel implements ListCellRenderer {
    private static final Border SELECTION_PAINTER = (Border)UIManager.get("MenuItem.selectedBackgroundPainter");

    private TreeModel myTreeModel;
    private boolean mySelected;
    private boolean myInList;
    private JComboBox myComboBox;
    private boolean myChecked;
    private boolean myEditable;
    private int myLevel = 0;
    private JTree myTree;
    private boolean myUnderAquaLookAndFeel;

    private TreeListCellRenderer(@NotNull final JComboBox comboBox, @NotNull final TreeModel model) {
      myComboBox = comboBox;
      myTreeModel = model;
      myTree = new JTree(myTreeModel);
      TreeUtil.expandAll(myTree);
      myTree.setRootVisible(true);
      myUnderAquaLookAndFeel = UIUtil.isUnderAquaLookAndFeel();
      setOpaque(!myUnderAquaLookAndFeel);
    }

    @Override
    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      myInList = index >= 0;
      if (index >= 0) {
        Object obj1 = myComboBox.getItemAt(index);
        myChecked = obj1 != null && obj1.equals(myComboBox.getSelectedItem());
      }
      else {
        myChecked = false;
      }

      if (value instanceof Iconable) {
        setIcon(((Iconable)value).getIcon(Iconable.ICON_FLAG_OPEN));
      }

      if (myInList) {
        final TreePath path = myTree.getPathForRow(index);
        myLevel = path == null ? 0 : path.getPathCount() - 1;
      } else {
        myLevel = 0;
      }

      myEditable = myComboBox.isEditable();

      setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      if (!myUnderAquaLookAndFeel) setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

      setText(value.toString());
      setSelected(isSelected);
      setFont(list.getFont());
      return this;
    }

    private void setSelected(final boolean selected) {
      mySelected = selected;
    }

    public Insets getInsets(Insets insets) {
      if (insets == null) insets = new Insets(0, 0, 0, 0);
      insets.top = 1;
      insets.bottom = 1;
      insets.right = 5;
      insets.left = !myInList || myEditable ? (myUnderAquaLookAndFeel ? 0 : 5)  : (myUnderAquaLookAndFeel ? 23 : 5) + (INDENT * myLevel);
      return insets;
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
    private TreeModel myTreeModel;
    private Object mySelectedItem;

    private TreeModelWrapper(@NotNull final TreeModel treeModel) {
      myTreeModel = treeModel;
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
      return count;
    }

    public Object getElementAt(int index) {
      Enumeration e = new PreorderEnumeration(myTreeModel);
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
      myStack = new Stack<Enumeration>();
      myStack.push(Collections.enumeration(Collections.singleton(treeModel.getRoot())));
    }

    public boolean hasMoreElements() {
      return (!myStack.empty() &&
              myStack.peek().hasMoreElements());
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
}
