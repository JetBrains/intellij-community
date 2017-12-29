// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.EventObject;

/**
 * @author Vladislav.Soroka
 * @since 3/25/14
 */
public class NewErrorTreeEditor extends AbstractCellEditor implements TreeCellEditor, MouseMotionListener {

  public static void install(Tree tree) {
    NewErrorTreeEditor treeEditor = new NewErrorTreeEditor(tree);
    tree.setCellEditor(treeEditor);
    tree.addMouseMotionListener(treeEditor);
    tree.setEditable(true);
  }

  private final MyWrapperEditor myWrapperEditor;
  private final CallingBackColoredTreeCellRenderer myColoredTreeCellRenderer;
  private final CellEditorDelegate myRightCellRenderer;
  private final JTree myTree;

  private NewErrorTreeEditor(JTree tree) {
    myTree = tree;
    myRightCellRenderer = new CellEditorDelegate();
    myColoredTreeCellRenderer = new CallingBackColoredTreeCellRenderer();
    myWrapperEditor = new MyWrapperEditor(myColoredTreeCellRenderer, myRightCellRenderer);
  }

  @Override
  public boolean isCellEditable(EventObject e) {
    Object node;
    if(e instanceof MouseEvent) {
      final Point point = ((MouseEvent)e).getPoint();
      final TreePath location = myTree.getClosestPathForLocation(point.x, point.y);
      node = location.getLastPathComponent();
    } else {
      node = myTree.getLastSelectedPathComponent();
    }
    final ErrorTreeElement element = getElement(node);
    return element instanceof EditableMessageElement;
  }

  @Override
  public Component getTreeCellEditorComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row) {
    final ErrorTreeElement element = getElement(value);
    if (element instanceof EditableMessageElement) {
      EditableMessageElement editableMessageElement = (EditableMessageElement)element;
      final CustomizeColoredTreeCellRenderer leftSelfRenderer = editableMessageElement.getLeftSelfRenderer();
      final TreeCellEditor rightSelfEditor = editableMessageElement.getRightSelfEditor();
      myColoredTreeCellRenderer.setCurrentCallback(leftSelfRenderer);
      myRightCellRenderer.setCurrentCallback(rightSelfEditor);
      return myWrapperEditor.getTreeCellEditorComponent(tree, value, selected, expanded, leaf, row);
    }
    return myTree.getCellRenderer().getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, true);
  }

  @Override
  public Object getCellEditorValue() {
    return null;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    JTree tree = (JTree)e.getSource();
    int selRow = tree.getRowForLocation(e.getX(), e.getY());
    if (selRow != -1) {
      TreePath treePath = tree.getPathForRow(selRow);
      if (treePath != null && treePath != tree.getEditingPath()) {
        final ErrorTreeElement element = getElement(treePath.getLastPathComponent());
        if (element instanceof EditableMessageElement && ((EditableMessageElement)element).startEditingOnMouseMove()) {
          if (!tree.isRowSelected(selRow)) {
            tree.setSelectionRow(selRow);
          }
          tree.startEditingAtPath(treePath);
        }
      }
    }
  }

  @Nullable
  private static ErrorTreeElement getElement(@Nullable Object value) {
    if (!(value instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    if (!(userObject instanceof ErrorTreeNodeDescriptor)) return null;
    return ((ErrorTreeNodeDescriptor)userObject).getElement();
  }

  private static class MyWrapperEditor extends AbstractCellEditor implements TreeCellEditor {
    private final TreeCellRenderer myLeft;
    private final TreeCellEditor myRight;
    private final JPanel myPanel;

    public TreeCellRenderer getLeft() {
      return myLeft;
    }

    public TreeCellEditor getRight() {
      return myRight;
    }

    public MyWrapperEditor(final TreeCellRenderer left, final TreeCellEditor right) {
      myLeft = left;
      myRight = right;
      myPanel = new JPanel(new BorderLayout());
    }

    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row) {
      myPanel.removeAll();
      myPanel.add(myLeft.getTreeCellRendererComponent(tree, value, false, expanded, leaf, row, true), BorderLayout.WEST);
      myPanel.add(myRight.getTreeCellEditorComponent(tree, value, selected, expanded, leaf, row), BorderLayout.EAST);

      if (UIUtil.isFullRowSelectionLAF()) {
        myPanel.setBackground(selected ? UIUtil.getTreeSelectionBackground() : null);
      }
      else if (WideSelectionTreeUI.isWideSelection(tree)) {
        if (selected) {
          myPanel.setBackground(UIUtil.getTreeSelectionBackground());
        }
      }
      else if (selected) {
        myPanel.setBackground(UIUtil.getTreeSelectionBackground());
      }
      else {
        myPanel.setBackground(null);
      }

      if (value instanceof LoadingNode) {
        myPanel.setForeground(JBColor.GRAY);
      }
      else {
        myPanel.setForeground(tree.getForeground());
      }

      if (UIUtil.isUnderGTKLookAndFeel() ||
          WideSelectionTreeUI.isWideSelection(tree)) {
        myPanel.setOpaque(false);
      }
      return myPanel;
    }

    @Override
    public Object getCellEditorValue() {
      return null;
    }
  }


  private static class CellEditorDelegate extends AbstractCellEditor implements TreeCellEditor {
    private TreeCellEditor myCurrentCallback;

    public Component getTreeCellEditorComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row) {
      return myCurrentCallback.getTreeCellEditorComponent(tree, value, selected, expanded, leaf, row);
    }

    public void setCurrentCallback(final TreeCellEditor currentCallback) {
      myCurrentCallback = currentCallback;
    }

    @Override
    public Object getCellEditorValue() {
      return null;
    }
  }
}
