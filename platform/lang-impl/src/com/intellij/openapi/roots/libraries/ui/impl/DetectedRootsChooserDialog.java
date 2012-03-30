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
package com.intellij.openapi.roots.libraries.ui.impl;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TitlePanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This dialog allows selecting paths inside selected archives or directories.
 *
 * @author max
 * @author Constantine.Plotnikov
 */
public class DetectedRootsChooserDialog extends DialogWrapper {
  /**
   * A tree with paths.  The tree relies on the CheckboxTree for selection and unselection policy.
   */
  private CheckboxTree myTree;
  /**
   * Root node for the tree. The tree is three-level:
   * <ul>
   * <li>The root is a fake node that just holds child nodes.</li>
   * <li>The second level is archives or directories selected on the previous selection step.</li>
   * <li>The third level are detected roots inside previous selection.</li>
   * </ul>
   */
  private CheckedTreeNode myRootNode;
  private JScrollPane myPane;
  private String myDescription;

  public DetectedRootsChooserDialog(Component component, List<SuggestedChildRootInfo> suggestedRoots) {
    super(component, true);
    init(suggestedRoots);
  }

  public DetectedRootsChooserDialog(Project project, List<SuggestedChildRootInfo> suggestedRoots) {
    super(project, true);
    init(suggestedRoots);
  }

  private void init(List<SuggestedChildRootInfo> suggestedRoots) {
    myDescription = "<html><body>" + ApplicationNamesInfo.getInstance().getFullProductName() +
                    " just scanned files and detected the following " + StringUtil.pluralize("root", suggestedRoots.size()) + ".<br>" +
                    "Select items in the tree below or press Cancel to cancel operation.</body></html>";
    myRootNode = createTree(suggestedRoots);
    myTree = createCheckboxTree();
    myPane = ScrollPaneFactory.createScrollPane(myTree);
    setTitle("Detected Roots");
    init();
  }

  private CheckboxTree createCheckboxTree() {
    CheckboxTree tree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(true) {
      public void customizeRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (!(value instanceof VirtualFileCheckedTreeNode)) return;
        VirtualFileCheckedTreeNode node = (VirtualFileCheckedTreeNode)value;
        VirtualFile file = node.getFile();
        String text;
        SimpleTextAttributes attributes;
        Icon icon;
        boolean isValid = true;
        if (leaf) {
          VirtualFile ancestor = ((VirtualFileCheckedTreeNode)node.getParent()).getFile();
          if (ancestor != null) {
            text = VfsUtilCore.getRelativePath(file, ancestor, File.separatorChar);
          }
          else {
            text = file.getPresentableUrl();
          }
          if (text == null) {
            isValid = false;
            text = file.getPresentableUrl();
          }
          attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
          icon = PlatformIcons.DIRECTORY_CLOSED_ICON;
        }
        else {
          text = file.getPresentableUrl();
          if (text == null) {
            isValid = false;
          }
          attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
          icon = expanded ? PlatformIcons.DIRECTORY_OPEN_ICON : PlatformIcons.DIRECTORY_CLOSED_ICON;
        }
        final ColoredTreeCellRenderer textRenderer = getTextRenderer();
        textRenderer.setIcon(icon);
        if (!isValid) {
          textRenderer.append("[INVALID] ", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        if (text != null) {
          textRenderer.append(text, attributes);
        }
        final String rootType = node.getRootType();
        if (rootType != null) {
          textRenderer.append(" [" + rootType + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    }, myRootNode);
    tree.setRootVisible(false);
    TreeUtil.expandAll(tree);
    return tree;
  }

  private static CheckedTreeNode createTree(List<SuggestedChildRootInfo> suggestedRoots) {
    TObjectIntHashMap<VirtualFile> rootTypesCount = new TObjectIntHashMap<VirtualFile>();
    for (SuggestedChildRootInfo suggestedRoot : suggestedRoots) {
      final VirtualFile root = suggestedRoot.getSuggestedRoot().getFile();
      if (!rootTypesCount.containsKey(root)) {
        rootTypesCount.put(root, 0);
      }
      rootTypesCount.increment(root);
    }

    CheckedTreeNode root = new CheckedTreeNode(null);
    Map<VirtualFile, CheckedTreeNode> rootCandidateNodes = new HashMap<VirtualFile, CheckedTreeNode>();
    for (SuggestedChildRootInfo rootInfo : suggestedRoots) {
      final VirtualFile rootCandidate = rootInfo.getRootCandidate();
      CheckedTreeNode parent = rootCandidateNodes.get(rootCandidate);
      if (parent == null) {
        parent = new VirtualFileCheckedTreeNode(rootCandidate);
        rootCandidateNodes.put(rootCandidate, parent);
        root.add(parent);
      }
      final String rootType = rootTypesCount.get(rootInfo.getSuggestedRoot().getFile()) > 1 ? rootInfo.getRootTypeName() : null;
      parent.add(new VirtualFileCheckedTreeNode(rootInfo, rootType));
    }
    return root;
  }

  @Override
  protected JComponent createTitlePane() {
    return new TitlePanel("Choose Roots", myDescription);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPane;
  }

  public SuggestedChildRootInfo[] getChosenRoots() {
    return myTree.getCheckedNodes(SuggestedChildRootInfo.class, null);
  }

  @NonNls
  @Override
  protected String getDimensionServiceKey() {
    return "DetectedRootsChooserDialog";
  }

  private static class VirtualFileCheckedTreeNode extends CheckedTreeNode {
    private final VirtualFile myFile;
    @Nullable private final String myRootType;

    private VirtualFileCheckedTreeNode(VirtualFile file) {
      super(file);
      myFile = file;
      myRootType = null;
    }

    public VirtualFileCheckedTreeNode(SuggestedChildRootInfo rootInfo, String rootType) {
      super(rootInfo);
      myFile = rootInfo.getSuggestedRoot().getFile();
      myRootType = rootType;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    public String getRootType() {
      return myRootType;
    }
  }
}
