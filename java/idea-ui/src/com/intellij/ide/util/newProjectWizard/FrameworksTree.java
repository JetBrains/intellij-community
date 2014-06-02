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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkOrGroup;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ui.*;
import com.intellij.util.containers.Convertor;
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

/**
 * @author nik
 */
public class FrameworksTree extends CheckboxTree {
  private boolean myProcessingMouseEventOnCheckbox;

  public FrameworksTree(FrameworkSupportModelBase model) {
    super(new FrameworksTreeRenderer(model), new CheckedTreeNode(), new CheckPolicy(false, true, true, false));
    setRootVisible(false);
    setShowsRootHandles(false);
    setRowHeight(0);
    putClientProperty("JTree.lineStyle", "None");
  }

  public void setRoots(List<FrameworkSupportNodeBase> roots) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    for (FrameworkSupportNodeBase base : roots) {
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
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath path) {
        final Object node = path.getLastPathComponent();
        if (node instanceof FrameworkSupportNodeBase) {
          return ((FrameworkSupportNodeBase)node).getTitle();
        }
        return "";
      }
    });
  }

  public boolean isProcessingMouseEventOnCheckbox() {
    return myProcessingMouseEventOnCheckbox;
  }

  private static class FrameworksTreeRenderer extends CheckboxTreeCellRenderer {
    private final FrameworkSupportModelBase myModel;

    private FrameworksTreeRenderer(FrameworkSupportModelBase model) {
      super(true, false);
      myModel = model;
      Border border = IdeBorderFactory.createEmptyBorder(2, 2, 2, 2);
      setBorder(border);
    }

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof FrameworkSupportNodeBase) {
        final FrameworkSupportNodeBase node = (FrameworkSupportNodeBase)value;
        SimpleTextAttributes attributes = node instanceof FrameworkGroupNode ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
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
    return !TreeUtil.traverse(root, new TreeUtil.Traverse() {
      @Override
      public boolean accept(Object node) {
        if (node instanceof FrameworkSupportNode && id.equals(((FrameworkSupportNode)node).getId())) {
          ((FrameworkSupportNode)node).setChecked(checked);
          return false;
        }
        return true;
      }
    });
  }
}
