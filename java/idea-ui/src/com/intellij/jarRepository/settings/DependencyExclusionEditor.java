// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.settings;

import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.eclipse.aether.artifact.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactDependencyNode;

import javax.swing.*;
import java.util.*;

class DependencyExclusionEditor {
  private final CheckboxTree myDependenciesTree;
  private final CheckedTreeNode myRootNode;
  private final JPanel myMainPanel;

  public DependencyExclusionEditor(ArtifactDependencyNode root, JPanel parentComponent) {
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
        if (!(userObject instanceof Artifact)) return;

        Artifact artifact = (Artifact)userObject;
        getTextRenderer().append(artifact.getGroupId() + ":" + artifact.getArtifactId(), SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
        getTextRenderer().append(":" + artifact.getVersion(), SimpleTextAttributes.GRAYED_ATTRIBUTES, true);
      }
    }, myRootNode, policy);
    myDependenciesTree.setRootVisible(false);
  }

  @Nullable
  public Set<String> selectExcludedDependencies(List<String> excludedDependencies) {
    uncheckExcludedNodes(myRootNode, new HashSet<>(excludedDependencies), false);
    TreeUtil.expandAll(myDependenciesTree);
    JPanel panel =
      JBUI.Panels.simplePanel(0, UIUtil.DEFAULT_VGAP)
                 .addToCenter(new JBScrollPane(myDependenciesTree))
                 .addToTop(new JBLabel(XmlStringUtil.wrapInHtml("Specify which transitive dependencies should be included into the library.")));
    DialogBuilder dialogBuilder =
      new DialogBuilder(myMainPanel)
        .title("Configure Transitive Dependencies")
        .centerPanel(panel);
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
    Artifact artifact = (Artifact)node.getUserObject();
    return artifact.getGroupId() + ":" + artifact.getArtifactId();
  }

  @NotNull
  private static CheckedTreeNode createDependencyTreeNode(ArtifactDependencyNode node) {
    CheckedTreeNode treeNode = new CheckedTreeNode(node.getArtifact());
    for (ArtifactDependencyNode dependency : node.getDependencies()) {
      treeNode.add(createDependencyTreeNode(dependency));
    }
    return treeNode;
  }
}
