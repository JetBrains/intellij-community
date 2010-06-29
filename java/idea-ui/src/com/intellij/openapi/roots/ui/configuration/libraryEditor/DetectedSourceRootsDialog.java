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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TitlePanel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Icons;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * This dialog allows selecting source paths inside selected source archives or directories.
 *
 * @author max
 * @author Constantine.Plotnikov
 */
public class DetectedSourceRootsDialog extends DialogWrapper {
  /**
   * A tree with paths.  The tree relies on the CheckboxTree for selection and unselection policy.
   */
  private final CheckboxTree myTree;
  /**
   * Root node for the tree. The tree is three-level:
   * <ul>
   * <li>The root is a fake node that just holds child nodes.</li>
   * <li>The second level is archives or directories selected on the previous selection step.</li>
   * <li>The third level are paths with java sources inside pervious selection.</li>
   * </ul>
   */
  private final CheckedTreeNode myRootNode;
  /**
   * A scrollable pane that contains myTree
   */
  private final JBScrollPane myPane;

  /**
   * A constructor
   *
   * @param project       a project context
   * @param detectedRoots a roots detected inside file
   * @param baseRoot      base root file
   */
  public DetectedSourceRootsDialog(Project project, List<VirtualFile> detectedRoots, VirtualFile baseRoot) {
    this(project, createTree(baseRoot, detectedRoots));
  }

  /**
   * A constructor
   *
   * @param project       a project context
   * @param detectedRoots a map from baseRoot to detected roots inside file
   */
  public DetectedSourceRootsDialog(Project project, Map<VirtualFile, List<VirtualFile>> detectedRoots) {
    this(project, createTree(detectedRoots));
  }

  /**
   * A constructor
   *
   * @param component     a parent component
   * @param detectedRoots a map from baseRoot to detected roots inside file
   */
  public DetectedSourceRootsDialog(Component component, Map<VirtualFile, List<VirtualFile>> detectedRoots) {
    this(component, createTree(detectedRoots));
  }

  /**
   * A constructor
   *
   * @param project a project context
   * @param tree    a checkbox tree to use
   */
  private DetectedSourceRootsDialog(Project project, CheckedTreeNode tree) {
    super(project, true);
    myRootNode = tree;
    myTree = createCheckboxTree();
    myPane = new JBScrollPane(myTree);
    setTitle("Detected Source Roots");
    init();
  }

  /**
   * A constructor
   *
   * @param component a parent component
   * @param tree      a checkbox tree to use
   */
  private DetectedSourceRootsDialog(Component component, CheckedTreeNode tree) {
    super(component, true);
    myRootNode = tree;
    myTree = createCheckboxTree();
    myPane = new JBScrollPane(myTree);
    setTitle("Detected Source Roots");
    init();
  }


  /**
   * Create a checkbox tree component for this dialog
   *
   * @return a created component
   */
  private CheckboxTree createCheckboxTree() {
    CheckboxTree tree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(true) {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        CheckedTreeNode node = (CheckedTreeNode)value;
        VirtualFile file = (VirtualFile)node.getUserObject();
        String text;
        SimpleTextAttributes attributes;
        Icon icon;
        boolean isValid = true;
        if (leaf) {
          VirtualFile ancestor = (VirtualFile)((CheckedTreeNode)node.getParent()).getUserObject();
          if (ancestor != null) {
            text = VfsUtil.getRelativePath(file, ancestor, File.separatorChar);
          }
          else {
            text = file.getPresentableUrl();
          }
          if (text == null) {
            isValid = false;
            text = file.getPresentableUrl();
          }
          attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
          icon = Icons.DIRECTORY_CLOSED_ICON;
        }
        else {
          text = file == null ? "found files" : file.getPresentableUrl();
          if (text == null) {
            isValid = false;
          }
          attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
          icon = expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON;
        }
        final ColoredTreeCellRenderer textRenderer = getTextRenderer();
        textRenderer.setIcon(icon);
        if (!isValid) {
          textRenderer.append("[INVALID] ", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        if (text != null) {
          textRenderer.append(text, attributes);
        }
      }
    }, myRootNode);
    tree.setRootVisible(false);
    TreeUtil.expandAll(tree);
    return tree;
  }

  /**
   * Create tree basing on baseRoot and detectedRoots
   *
   * @param detectedRoots a detected roots (map from base root to detected roots)
   * @return a root node of the tree
   */
  private static CheckedTreeNode createTree(Map<VirtualFile, List<VirtualFile>> detectedRoots) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    for (Map.Entry<VirtualFile, List<VirtualFile>> e : detectedRoots.entrySet()) {
      root.add(createTreeNode(e.getKey(), e.getValue()));
    }
    return root;
  }

  /**
   * Create tree basing on baseRoot and detectedRoots
   *
   * @param baseRoot      a base root
   * @param detectedRoots a detected roots
   * @return a root node of the tree
   */
  private static CheckedTreeNode createTree(final VirtualFile baseRoot, final List<VirtualFile> detectedRoots) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    root.add(createTreeNode(baseRoot, detectedRoots));
    return root;
  }

  /**
   * Create tree node from baseRoot and detectedRoots
   *
   * @param baseRoot      a base root
   * @param detectedRoots a detected roots
   * @return a root node for the base root
   */
  private static CheckedTreeNode createTreeNode(final VirtualFile baseRoot, final List<VirtualFile> detectedRoots) {
    CheckedTreeNode node = new CheckedTreeNode(baseRoot);
    for (VirtualFile f : detectedRoots) {
      node.add(new CheckedTreeNode(f));
    }
    return node;
  }

  @Override
  protected JComponent createTitlePane() {
    return new TitlePanel("Choose Source Roots", "<html><body>IntelliJ IDEA just scanned files and detected following source root(s).<br>" +
                                                 "Select items in the tree below or press Cancel to cancel operation.</body></html>");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPane;
  }

  /**
   * @return roots that has been chosen using this dialog
   */
  public List<VirtualFile> getChosenRoots() {
    ArrayList<VirtualFile> rc = new ArrayList<VirtualFile>();
    for (Enumeration be = myRootNode.children(); be.hasMoreElements();) {
      CheckedTreeNode baseFileNode = (CheckedTreeNode)be.nextElement();
      for (Enumeration de = baseFileNode.children(); de.hasMoreElements();) {
        CheckedTreeNode dirNode = (CheckedTreeNode)de.nextElement();
        if (dirNode.isChecked()) {
          rc.add((VirtualFile)dirNode.getUserObject());
        }
      }
    }
    return rc;
  }

  @NonNls
  @Override
  protected String getDimensionServiceKey() {
    return "DetectedSourceRootsDialog";
  }
}
