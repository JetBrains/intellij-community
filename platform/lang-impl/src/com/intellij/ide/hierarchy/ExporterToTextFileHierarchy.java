/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.hierarchy;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;

class ExporterToTextFileHierarchy implements ExporterToTextFile {
  private static final Logger LOG = Logger.getInstance(ExporterToTextFileHierarchy.class);
  private final HierarchyBrowserBase myHierarchyBrowserBase;

  public ExporterToTextFileHierarchy(@NotNull HierarchyBrowserBase hierarchyBrowserBase) {
    myHierarchyBrowserBase = hierarchyBrowserBase;
  }

  @NotNull
  @Override
  public String getReportText() {
    StringBuilder buf = new StringBuilder();
    HierarchyTreeBuilder currentBuilder = myHierarchyBrowserBase.getCurrentBuilder();
    LOG.assertTrue(currentBuilder != null);
    appendNode(buf, currentBuilder.getRootNode(), SystemProperties.getLineSeparator(), "");
    return buf.toString();
  }

  private void appendNode(StringBuilder buf, DefaultMutableTreeNode node, String lineSeparator, String indent) {
    final String childIndent;
    if (node.getParent() != null) {
      childIndent = indent + "    ";
      final HierarchyNodeDescriptor descriptor = myHierarchyBrowserBase.getDescriptor(node);
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
  
  @NotNull
  @Override
  public String getDefaultFilePath() {
    final HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myHierarchyBrowserBase.myProject).getState();
    return state != null && state.EXPORT_FILE_PATH != null ? state.EXPORT_FILE_PATH : "";
  }

  @Override
  public void exportedTo(@NotNull String filePath) {
    final HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myHierarchyBrowserBase.myProject).getState();
    if (state != null) {
      state.EXPORT_FILE_PATH = filePath;
    }
  }

  @Override
  public boolean canExport() {
    return myHierarchyBrowserBase.getCurrentBuilder() != null;
  }
}
