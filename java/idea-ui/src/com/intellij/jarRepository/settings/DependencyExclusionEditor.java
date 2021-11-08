// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.settings;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.eclipse.aether.artifact.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactDependencyNode;

import javax.swing.*;
import java.util.*;

class DependencyExclusionEditor {
  private static final SimpleTextAttributes STRIKEOUT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);
  private static final SimpleTextAttributes STRIKEOUT_GRAYED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT,
                                                                                                   UIUtil.getInactiveTextColor());
  private final CheckboxTree myDependenciesTree;
  private final CheckedTreeNode myRootNode;
  private final JPanel myMainPanel;

  DependencyExclusionEditor(ArtifactDependencyNode root, JPanel parentComponent) {
    myMainPanel = parentComponent;
    myRootNode = createDependencyTreeNode(root);
    CheckboxTreeBase.CheckPolicy policy = new CheckboxTreeBase.CheckPolicy(false, true, true, false);
    myDependenciesTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {
      {
        myIgnoreInheritance = true;
      }

      @Override
      public void customizeRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
        if (!(value instanceof CheckedTreeNode)) return;

        Object userObject = ((CheckedTreeNode)value).getUserObject();
        if (!(userObject instanceof ArtifactDependencyNode)) return;

        ArtifactDependencyNode node = (ArtifactDependencyNode)userObject;
        Artifact artifact = node.getArtifact();
        boolean rejected = node.isRejected();
        @NlsSafe final String groupArtifactFragment = artifact.getGroupId() + ":" + artifact.getArtifactId();
        getTextRenderer().append(groupArtifactFragment, !rejected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : STRIKEOUT_ATTRIBUTES, true);
        @NlsSafe final String versionFragment = ":" + artifact.getVersion();
        getTextRenderer().append(versionFragment, !rejected ? SimpleTextAttributes.GRAYED_ATTRIBUTES : STRIKEOUT_GRAYED_ATTRIBUTES, true);
        setToolTipText(rejected ? JavaUiBundle.message("tooltip.text.dependency.was.rejected") : null);
      }
    }, myRootNode, policy) {
      @Override
      protected void installSpeedSearch() {
        new TreeSpeedSearch(this, treePath -> {
          Object node = treePath.getLastPathComponent();
          if (!(node instanceof CheckedTreeNode)) return "";
          Object data = ((CheckedTreeNode)node).getUserObject();
          if (!(data instanceof ArtifactDependencyNode)) return "";
          Artifact artifact = ((ArtifactDependencyNode)data).getArtifact();
          return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
        });
      }
    };
    myDependenciesTree.setRootVisible(false);
    myDependenciesTree.addCheckboxTreeListener(new CheckboxTreeListener() {
      private boolean myProcessingNodes;

      @Override
      public void nodeStateChanged(@NotNull CheckedTreeNode node) {
        if (myProcessingNodes) return;

        myProcessingNodes = true;
        try {
          if (!node.isChecked()) {
            String groupAndArtifact = getGroupAndArtifactId(node);
            /*
              exclusion works by groupId and artifactId, so if there are other nodes with same groupId and artifactId, we need to uncheck
              them to avoid confusion
            */
            TreeUtil.treeNodeTraverser(myRootNode).filter(CheckedTreeNode.class).forEach((treeNode) -> {
              if (getGroupAndArtifactId(treeNode).equals(groupAndArtifact)) {
                myDependenciesTree.setNodeState(treeNode, false);
              }
            });
          }
        }
        finally {
          myProcessingNodes = false;
        }
      }
    });
  }

  @Nullable
  public Set<String> selectExcludedDependencies(List<String> excludedDependencies) {
    uncheckExcludedNodes(myRootNode, new HashSet<>(excludedDependencies), false);
    TreeUtil.expandAll(myDependenciesTree);
    DialogBuilder dialogBuilder =
      new DialogBuilder(myMainPanel)
        .title(JavaUiBundle.message("dialog.title.include.transitive.dependencies"))
        .centerPanel(new JBScrollPane(myDependenciesTree));
    dialogBuilder.setPreferredFocusComponent(myDependenciesTree);

    if (dialogBuilder.showAndGet()) {
      return collectUncheckedNodes(myRootNode, new LinkedHashSet<>());
    }
    return null;
  }

  private static void uncheckExcludedNodes(CheckedTreeNode node, Set<String> excluded, boolean parentIsExcluded) {
    boolean isExcluded = parentIsExcluded || excluded.contains(getGroupAndArtifactId(node));
    node.setChecked(!isExcluded);
    Enumeration children = node.children();
    while (children.hasMoreElements()) {
      Object child = children.nextElement();
      uncheckExcludedNodes((CheckedTreeNode)child, excluded, isExcluded);
    }
  }

  private static Set<String> collectUncheckedNodes(CheckedTreeNode node, Set<String> result) {
    if (node.isChecked()) {
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        Object child = children.nextElement();
        collectUncheckedNodes((CheckedTreeNode)child, result);
      }
    }
    else {
      result.add(getGroupAndArtifactId(node));
    }
    return result;
  }

  @NotNull
  private static String getGroupAndArtifactId(CheckedTreeNode node) {
    Artifact artifact = ((ArtifactDependencyNode)node.getUserObject()).getArtifact();
    return artifact.getGroupId() + ":" + artifact.getArtifactId();
  }

  @NotNull
  private static CheckedTreeNode createDependencyTreeNode(ArtifactDependencyNode node) {
    CheckedTreeNode treeNode = new CheckedTreeNode(node);
    for (ArtifactDependencyNode dependency : node.getDependencies()) {
      treeNode.add(createDependencyTreeNode(dependency));
    }
    return treeNode;
  }
}
