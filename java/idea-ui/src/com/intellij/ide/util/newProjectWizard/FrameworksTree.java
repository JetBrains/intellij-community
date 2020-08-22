// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkOrGroup;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class FrameworksTree extends CheckboxTree {
  private boolean myProcessingMouseEventOnCheckbox;

  public FrameworksTree(FrameworkSupportModelBase model) {
    super(new FrameworksTreeRenderer(model), new CheckedTreeNode(), new CheckPolicy(false, true, true, false));
    setRootVisible(false);
    setShowsRootHandles(false);
    setRowHeight(0);
    putClientProperty("JTree.lineStyle", "None");
  }

  public void setRoots(List<? extends FrameworkSupportNodeBase> roots) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    for (FrameworkSupportNodeBase<?> base : roots) {
      root.add(base);
    }
    setModel(new DefaultTreeModel(root));
    TreeUtil.expandAll(this);
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    final TreePath path = getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final Object node = path.getLastPathComponent();
      if (node instanceof FrameworkSupportNode) {
        final Rectangle checkboxBounds = ((CheckboxTreeCellRendererBase)getCellRenderer()).myCheckbox.getBounds();
        final Rectangle pathBounds = getPathBounds(path);
        assert pathBounds != null;
        checkboxBounds.setLocation(pathBounds.getLocation());
        if (checkboxBounds.contains(e.getPoint())) {
          try {
            myProcessingMouseEventOnCheckbox = true;
            super.processMouseEvent(e);
          }
          finally {
            myProcessingMouseEventOnCheckbox = false;
          }
          return;
        }
      }
    }

    super.processMouseEvent(e);
  }

  @Override
  protected void installSpeedSearch() {
    new TreeSpeedSearch(this, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof FrameworkSupportNodeBase) {
        return ((FrameworkSupportNodeBase<?>)node).getTitle();
      }
      return "";
    });
  }

  public boolean isProcessingMouseEventOnCheckbox() {
    return myProcessingMouseEventOnCheckbox;
  }

  private static final class FrameworksTreeRenderer extends CheckboxTreeCellRenderer {
    private final FrameworkSupportModelBase myModel;

    private FrameworksTreeRenderer(FrameworkSupportModelBase model) {
      super(true, false);
      myModel = model;
      Border border = JBUI.Borders.empty(2);
      setBorder(border);
    }

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof FrameworkSupportNodeBase) {
        FrameworkSupportNodeBase<?> node = (FrameworkSupportNodeBase<?>)value;
        SimpleTextAttributes attributes = node instanceof FrameworkGroupNode ?
                                          SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        getTextRenderer().append(node.getTitle(), attributes);
        if (node.isChecked()) {
          FrameworkOrGroup object = node.getUserObject();
          String version = myModel.getFrameworkVersion(object.getId());
          if (version != null) {
            getTextRenderer().append(" (" + version + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
        getTextRenderer().setIcon(node.getIcon());
        getCheckbox().setVisible(value instanceof FrameworkSupportNode);
      }
    }
  }

  @TestOnly
  public boolean selectFramework(final String id, final boolean checked) {
    TreeNode root = (TreeNode)getModel().getRoot();
    return !TreeUtil.traverse(root, node -> {
      if (node instanceof FrameworkSupportNode && id.equals(((FrameworkSupportNode)node).getId())) {
        ((FrameworkSupportNode)node).setChecked(checked);
        return false;
      }
      return true;
    });
  }
}
