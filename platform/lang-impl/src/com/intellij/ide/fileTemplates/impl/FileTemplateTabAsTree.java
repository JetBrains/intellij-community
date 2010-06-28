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

package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
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

/**
 * @author Alexey Kudravtsev
 */
abstract class FileTemplateTabAsTree extends FileTemplateTab {
  private final JTree myTree;
  private final FileTemplateNode myRoot;
  private final MyTreeModel myTreeModel;

  protected FileTemplateTabAsTree(String title) {
    super(title);
    myRoot = initModel();
    myTreeModel = new MyTreeModel(myRoot);
    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);

    myTree.expandPath(TreeUtil.getPathFromRoot(myRoot));
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new MyTreeCellRenderer());
    myTree.expandRow(0);

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        onTemplateSelected();
      }
    });
  }

  protected abstract FileTemplateNode initModel();
  protected static class FileTemplateNode extends DefaultMutableTreeNode {
    private Icon myIcon;
    private final String myTemplate;

    FileTemplateNode(FileTemplateDescriptor descriptor) {
      this(descriptor.getDisplayName(),
           descriptor.getIcon(),
           descriptor instanceof FileTemplateGroupDescriptor ? ContainerUtil.map2List(((FileTemplateGroupDescriptor)descriptor).getTemplates(), new Function<FileTemplateDescriptor, FileTemplateNode>() {
             public FileTemplateNode fun(FileTemplateDescriptor s) {
               return new FileTemplateNode(s);
             }
           }) : Collections.<FileTemplateNode>emptyList(),
           descriptor instanceof FileTemplateGroupDescriptor ? null : descriptor.getFileName());
    }

    FileTemplateNode(String name, Icon icon, List<FileTemplateNode> children) {
      this(name, icon, children, null);
    }

    FileTemplateNode(Icon icon, String template) {
      this(template, icon, Collections.<FileTemplateNode>emptyList(), template);
    }

    private FileTemplateNode(String name, Icon icon, List<FileTemplateNode> children, String template) {
      super(name);
      myIcon = icon;
      myTemplate = template;
      for (FileTemplateNode child : children) {
        add(child);
      }
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getTemplate() {
      return myTemplate;
    }

  }

  private static class MyTreeModel extends DefaultTreeModel {
    MyTreeModel(FileTemplateNode root) {
      super(root);
    }
  }

  private class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

      if (value instanceof FileTemplateNode) {
        final FileTemplateNode node = (FileTemplateNode)value;
        setText((String) node.getUserObject());
        setIcon(node.getIcon());
        setFont(getFont().deriveFont(AllFileTemplatesConfigurable.isInternalTemplate(node.getTemplate(), getTitle()) ? Font.BOLD : Font.PLAIN));

        final FileTemplate template = getTemplate(node);
        if (template != null && !template.isDefault()) {
          if (!sel) {
            super.setForeground(MODIFIED_FOREGROUND);
          }
        }
      }
      return this;
    }
  }

  public void removeSelected() {
    // not supported
  }

  protected void initSelection(FileTemplate selection) {
    if (selection != null) {
      selectTemplate(selection);
    }
    else {
      TreeUtil.selectFirstNode(myTree);
    }
  }

  public void selectTemplate(FileTemplate template) {
    String name = template.getName();
    if (template.getExtension().length() > 0) {
      name += "." + template.getExtension();
    }
    
    final FileTemplateNode node = (FileTemplateNode)TreeUtil.findNodeWithObject(myRoot, name);
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
      onTemplateSelected(); // this is important because we select different Template for the same node
    }
  }

  @Nullable
  public FileTemplate getSelectedTemplate() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return null;
    final FileTemplateNode node = (FileTemplateNode)selectionPath.getLastPathComponent();
    return getTemplate(node);
  }

  @Nullable
  private FileTemplate getTemplate(final FileTemplateNode node) {
    final String template = node.getTemplate();
    return template == null || savedTemplates == null ? null : savedTemplates.get(FileTemplateManager.getInstance().getJ2eeTemplate(template));
  }

  public JComponent getComponent() {
    return myTree;
  }

  public void fireDataChanged() {
  }

  public void addTemplate(FileTemplate newTemplate) {
    // not supported
  }
}
