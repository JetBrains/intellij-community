// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

abstract class FileTemplateTabAsTree extends FileTemplateTab {
  private final JTree myTree;
  private final FileTemplateNode myRoot;

  FileTemplateTabAsTree(@NlsContexts.TabTitle String title) {
    super(title);
    myRoot = initModel();
    MyTreeModel treeModel = new MyTreeModel(myRoot);
    myTree = new Tree(treeModel);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandPath(TreeUtil.getPathFromRoot(myRoot));
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new MyTreeCellRenderer());
    myTree.expandRow(0);

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        onTemplateSelected();
      }
    });
    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);
  }

  protected abstract FileTemplateNode initModel();

  protected static final class FileTemplateNode extends DefaultMutableTreeNode {
    private final Icon myIcon;
    private final String myTemplateName;

    FileTemplateNode(FileTemplateDescriptor descriptor) {
      this(descriptor.getDisplayName(),
           descriptor.getIcon(),
           descriptor instanceof FileTemplateGroupDescriptor ? ContainerUtil.map(((FileTemplateGroupDescriptor)descriptor).getTemplates(),
                                                                                      s -> new FileTemplateNode(s)) : Collections.emptyList(),
           descriptor instanceof FileTemplateGroupDescriptor ? null : descriptor.getFileName());
    }

    FileTemplateNode(String name, Icon icon, List<? extends FileTemplateNode> children) {
      this(name, icon, children, null);
    }

    private FileTemplateNode(String name, Icon icon, List<? extends FileTemplateNode> children, String templateName) {
      super(name);
      myIcon = icon;
      myTemplateName = templateName;
      for (FileTemplateNode child : children) {
        add(child);
      }
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getTemplateName() {
      return myTemplateName;
    }

  }

  private static final class MyTreeModel extends DefaultTreeModel {
    private MyTreeModel(FileTemplateNode root) {
      super(root);
    }
  }

  private final class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      setBorderSelectionColor(null);
      setBackgroundSelectionColor(null);
      setBackgroundNonSelectionColor(null);
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      setBackground(UIUtil.getTreeBackground(sel, hasFocus));
      setForeground(UIUtil.getTreeForeground(sel, hasFocus));

      if (value instanceof FileTemplateNode node) {
        setText((String) node.getUserObject());
        setIcon(node.getIcon());

        final FileTemplate template = getTemplate(node);
        if (template != null && !template.isDefault()) {
          if (!sel) {
            setForeground(MODIFIED_FOREGROUND);
          }
        }
      }
      return this;
    }
  }

  @Override
  public void removeSelected() {
    // not supported
  }

  @Override
  protected void initSelection(FileTemplate selection) {
    if (selection != null) {
      selectTemplate(selection);
    }
    else {
      TreeUtil.promiseSelectFirst(myTree);
    }
  }

  @Override
  public void selectTemplate(FileTemplate template) {
    String name = template.getName();
    if (!template.getExtension().isEmpty()) {
      name += "." + template.getExtension();
    }

    final FileTemplateNode node = (FileTemplateNode)TreeUtil.findNodeWithObject(myRoot, name);
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
      onTemplateSelected(); // this is important because we select different Template for the same node
    }
  }

  @Override
  public @Nullable FileTemplate getSelectedTemplate() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) {
      return null;
    }
    final FileTemplateNode node = (FileTemplateNode)selectionPath.getLastPathComponent();
    return getTemplate(node);
  }

  private @Nullable FileTemplate getTemplate(final FileTemplateNode node) {
    final String templateName = node.getTemplateName();
    if (templateName == null || templates.isEmpty()) {
      return null;
    }
    for (FileTemplateBase template : templates) {
      if (templateName.equals(template.getQualifiedName()) || templateName.equals(template.getName())) {
        return template;
      }
    }
    return null;
  }

  @Override
  public JComponent getComponent() {
    return myTree;
  }

  @Override
  public void fireDataChanged() {
  }

  @Override
  public void addTemplate(FileTemplate newTemplate) {
    // not supported
  }

  @Override
  public void insertTemplate(FileTemplate newTemplate, int index) {
    // not supported
  }
}
