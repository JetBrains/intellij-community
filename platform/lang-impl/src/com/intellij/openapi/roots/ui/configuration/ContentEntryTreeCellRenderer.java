/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;

public class ContentEntryTreeCellRenderer extends NodeRenderer {
  protected final ContentEntryTreeEditor myTreeEditor;

  public ContentEntryTreeCellRenderer(@NotNull final ContentEntryTreeEditor treeEditor) {
    myTreeEditor = treeEditor;
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

    final ContentEntryEditor editor = myTreeEditor.getContentEntryEditor();
    if (editor != null) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof NodeDescriptor) {
        final Object element = ((NodeDescriptor)userObject).getElement();
        if (element instanceof FileElement) {
          final String path = ((FileElement)element).getPath();
          if (new File(path).isDirectory()) {
            final ContentEntry contentEntry = editor.getContentEntry();
            if (contentEntry != null) {
              final String prefix = getPrefix(contentEntry, path);
              if (prefix.length() > 0) {
                append(" (" + prefix + ")", new SimpleTextAttributes(Font.PLAIN, Color.GRAY));
              }
              setIcon(updateIcon(contentEntry, path, getIcon(), expanded));
            }
          }
        }
      }
    }
  }

  private static String getPrefix(final ContentEntry entry, final String path) {
    final String url = VfsUtil.pathToUrl(path);
    for (final SourceFolder sourceFolder : entry.getSourceFolders()) {
      if (url.equals(sourceFolder.getUrl())) {
        return sourceFolder.getPackagePrefix();
      }
    }
    return "";
  }

  protected Icon updateIcon(final ContentEntry entry, final String path, final Icon originalIcon, final boolean expanded) {
    for (ExcludeFolder excludeFolder : entry.getExcludeFolders()) {
      final String excludePath = VfsUtil.urlToPath(excludeFolder.getUrl());
      if (FileUtil.isAncestor(excludePath, path, false)) {
        return IconSet.getExcludeIcon(expanded);
      }
    }

    final SourceFolder[] sourceFolders = entry.getSourceFolders();
    for (SourceFolder sourceFolder : sourceFolders) {
      final String sourcePath = VfsUtil.urlToPath(sourceFolder.getUrl());
      if (sourcePath.equals(path)) {
        return IconSet.getSourceRootIcon(sourceFolder.isTestSource(), expanded);
      }
    }

    Icon icon = originalIcon;
    String currentRoot = null;
    for (SourceFolder sourceFolder : sourceFolders) {
      final String sourcePath = VfsUtil.urlToPath(sourceFolder.getUrl());
      if (FileUtil.isAncestor(sourcePath, path, true)) {
        if (currentRoot != null && FileUtil.isAncestor(sourcePath, currentRoot, false)) {
          continue;
        }
        icon = IconSet.getSourceFolderIcon(sourceFolder.isTestSource(), expanded);
        currentRoot = sourcePath;
      }
    }
    return icon;
  }

  /** @deprecated use {@linkplain #updateIcon(com.intellij.openapi.roots.ContentEntry, String, javax.swing.Icon, boolean)} (to remove in IDEA 12) */
  @SuppressWarnings("UnusedDeclaration")
  protected Icon updateIcon(final ContentEntry entry, final VirtualFile file, Icon originalIcon, final boolean expanded) {
    return updateIcon(entry, file.getPath(), originalIcon, expanded);
  }
}
