/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.hierarchy;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;
import java.util.TooManyListenersException;

class ExporterToTextFileHierarchy implements ExporterToTextFile {
  private final HierarchyBrowserBase myHierarchyBrowserBase;

  public ExporterToTextFileHierarchy(@NotNull HierarchyBrowserBase hierarchyBrowserBase) {
    myHierarchyBrowserBase = hierarchyBrowserBase;
  }

  @Override
  public JComponent getSettingsEditor() {
    return null;
  }

  @Override
  public void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException {
  }

  @Override
  public void removeSettingsChangedListener(ChangeListener listener) {
  }

  @NotNull
  @Override
  public String getReportText() {
    StringBuilder buf = new StringBuilder();
    appendNode(buf, myHierarchyBrowserBase.getCurrentBuilder().getRootNode(), SystemProperties.getLineSeparator(), "");
    return buf.toString();
  }

  private void appendNode(StringBuilder buf, DefaultMutableTreeNode node, String lineSeparator, String indent) {
    buf.append(indent);
    final String childIndent;
    if (node.getParent() != null) {
      childIndent = indent + "    ";
      final HierarchyNodeDescriptor descriptor = myHierarchyBrowserBase.getDescriptor(node);
      if (descriptor != null) {
        buf.append(descriptor.getHighlightedText().getText()).append(lineSeparator);
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
  public void exportedTo(String filePath) {
    final HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myHierarchyBrowserBase.myProject).getState();
    if (state != null) {
      state.EXPORT_FILE_PATH = filePath;
    }
  }

  @Override
  public boolean canExport() {
    return true;
  }
}
