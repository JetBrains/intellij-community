// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.ui.CheckboxTree;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class PushLogTreeUtil {

  public static final String EDIT_MODE_PROP = "tree.edit.mode";

  @Nullable
  public static Object getTagAtForRenderer(CheckboxTree.CheckboxTreeCellRenderer renderer, MouseEvent e) {
    JTree tree = (JTree)e.getSource();
    Object tag = null;
    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final Rectangle rectangle = tree.getPathBounds(path);
      assert rectangle != null;
      int dx = e.getX() - rectangle.x;
      final TreeNode treeNode = (TreeNode)path.getLastPathComponent();
      final int row = tree.getRowForLocation(e.getX(), e.getY());
      tree.getCellRenderer().getTreeCellRendererComponent(tree, treeNode, false, false, true, row, true);
      if (treeNode instanceof RepositoryNode) {
        RepositoryNode repositoryNode = (RepositoryNode)treeNode;
        int checkBoxWidth = repositoryNode.isCheckboxVisible() ? renderer.getCheckbox().getWidth() : 0;
        tag = renderer.getTextRenderer().getFragmentTagAt(dx - checkBoxWidth);
      }
      else {
        tag = renderer.getTextRenderer().getFragmentTagAt(dx);
      }
    }
    return tag;
  }

  public static SimpleTextAttributes addTransparencyIfNeeded(@NotNull SimpleColoredComponent component,
                                                             @NotNull SimpleTextAttributes baseStyle,
                                                             boolean isActive) {
    if (isActive) return baseStyle;
    Color color = ObjectUtils.chooseNotNull(baseStyle.getFgColor(), component.getForeground());
    return new SimpleTextAttributes(baseStyle.getStyle(), ColorUtil.toAlpha(color, 85));
  }
}
