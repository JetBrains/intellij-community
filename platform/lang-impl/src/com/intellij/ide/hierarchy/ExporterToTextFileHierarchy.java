// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.tree.StructureTreeModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;

final class ExporterToTextFileHierarchy implements ExporterToTextFile {
  private static final Logger LOG = Logger.getInstance(ExporterToTextFileHierarchy.class);
  private final HierarchyBrowserBase myHierarchyBrowserBase;

  ExporterToTextFileHierarchy(@NotNull HierarchyBrowserBase hierarchyBrowserBase) {
    myHierarchyBrowserBase = hierarchyBrowserBase;
  }

  @Override
  public @NotNull String getReportText() {
    StringBuilder buf = new StringBuilder();
    StructureTreeModel currentBuilder = myHierarchyBrowserBase.getCurrentBuilder();
    LOG.assertTrue(currentBuilder != null);
    appendNode(buf, currentBuilder.getRootImmediately(), System.lineSeparator(), "");
    return buf.toString();
  }

  private static void appendNode(StringBuilder buf, @NotNull TreeNode node, String lineSeparator, String indent) {
    String childIndent;
    if (node.getParent() != null) {
      childIndent = indent + "    ";
      HierarchyNodeDescriptor descriptor = HierarchyBrowserBase.getDescriptor((DefaultMutableTreeNode)node);
      if (descriptor != null) {
        buf.append(indent).append(descriptor.getHighlightedText().getText()).append(lineSeparator);
      }
    }
    else {
      childIndent = indent;
    }

    Enumeration enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)enumeration.nextElement();
      appendNode(buf, child, lineSeparator, childIndent);
    }
  }
  
  @Override
  public @NotNull String getDefaultFilePath() {
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myHierarchyBrowserBase.myProject).getState();
    return state != null && state.EXPORT_FILE_PATH != null ? state.EXPORT_FILE_PATH : "";
  }

  @Override
  public void exportedTo(@NotNull String filePath) {
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myHierarchyBrowserBase.myProject).getState();
    if (state != null) {
      state.EXPORT_FILE_PATH = filePath;
    }
  }

  @Override
  public boolean canExport() {
    return myHierarchyBrowserBase.getCurrentBuilder() != null;
  }
}
