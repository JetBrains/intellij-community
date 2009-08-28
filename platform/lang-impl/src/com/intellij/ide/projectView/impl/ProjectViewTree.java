package com.intellij.ide.projectView.impl;

import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.KeyEvent;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 17, 2003
 * Time: 7:44:22 PM
 */
public abstract class ProjectViewTree extends DnDAwareTree {
  protected ProjectViewTree(TreeModel newModel) {
    super(newModel);

    if (SystemInfo.isMac) setUI(new UIUtil.LeglessTreeUi());

    final NodeRenderer renderer = new NodeRenderer();
    renderer.setOpaque(false);
    renderer.setIconOpaque(false);
    setCellRenderer(renderer);

    //setOpaque(false);
  }

  @Override
  public void setUI(final TreeUI ui) {
    TreeUI actualUI = ui;
    if (SystemInfo.isMac && !(ui instanceof UIUtil.LeglessTreeUi)) {
      actualUI = new UIUtil.LeglessTreeUi();
    }

    super.setUI(actualUI);
  }

  @Override
  public void processKeyEvent(final KeyEvent e) {
    TreePath path = getSelectionPath();
    if (path != null) {
      if (e.getKeyCode() == KeyEvent.VK_LEFT) {
        if (isExpanded(path)) {
          collapsePath(path);
          return;
        }
      } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
        if (isCollapsed(path)) {
          expandPath(path);
          return;
        }
      }
    }

    super.processKeyEvent(e);
  }

  public final int getToggleClickCount() {
    DefaultMutableTreeNode node = getSelectedNode();
    if (node != null) {
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)userObject;
        if (!descriptor.expandOnDoubleClick()) return -1;
      }
    }
    return super.getToggleClickCount();
  }

  public abstract DefaultMutableTreeNode getSelectedNode();
}
