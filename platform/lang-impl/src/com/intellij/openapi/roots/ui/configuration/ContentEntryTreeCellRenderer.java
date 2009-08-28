package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class ContentEntryTreeCellRenderer extends NodeRenderer {
  protected final ContentEntryTreeEditor myTreeEditor;

  public ContentEntryTreeCellRenderer(ContentEntryTreeEditor treeEditor) {
    myTreeEditor = treeEditor;
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
    final ContentEntryEditor contentEntryEditor = myTreeEditor.getContentEntryEditor();
    if (contentEntryEditor == null) {
      return;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    if (!(node.getUserObject() instanceof  NodeDescriptor)) {
      return;
    }
    NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    final Object element = descriptor.getElement();
    if (element instanceof FileElement) {
      final VirtualFile file = ((FileElement)element).getFile();
      if (file != null && file.isDirectory()) {
        final ContentEntry contentEntry = contentEntryEditor.getContentEntry();
        final String prefix = getPrefix(contentEntry, file);
        if (prefix.length() > 0) {
          append(" (" + prefix + ")", new SimpleTextAttributes(Font.PLAIN, Color.GRAY));
        }
        final Icon updatedIcon = updateIcon(contentEntry, file, getIcon(), expanded);
        setIcon(updatedIcon);
      }
    }
  }

  private String getPrefix(final ContentEntry entry, final VirtualFile file) {
    final SourceFolder[] sourceFolders = entry.getSourceFolders();
    final String url = file.getUrl();
    for (final SourceFolder sourceFolder : sourceFolders) {
      if (url.equals(sourceFolder.getUrl())) {
        return sourceFolder.getPackagePrefix();
      }
    }
    return "";
  }

  protected Icon updateIcon(final ContentEntry entry, final VirtualFile file, Icon originalIcon, final boolean expanded) {
    final ExcludeFolder[] excludeFolders = entry.getExcludeFolders();
    for (ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile f = excludeFolder.getFile();
      if (f == null) {
        continue;
      }

      if (VfsUtil.isAncestor(f, file, false)) {
        return IconSet.getExcludeIcon(expanded);
      }
    }

    final SourceFolder[] sourceFolders = entry.getSourceFolders();
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile f = sourceFolder.getFile();
      if (f == null) {
        continue;
      }
      if (f.equals(file)) {
        return IconSet.getSourceRootIcon(sourceFolder.isTestSource(), expanded);
      }
    }

    VirtualFile currentRoot = null;
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile f = sourceFolder.getFile();
      if (f == null) {
        continue;
      }
      if (VfsUtil.isAncestor(f, file, true)) {
        if (currentRoot != null && VfsUtil.isAncestor(f, currentRoot, false)) {
          continue;
        }
        originalIcon = IconSet.getSourceFolderIcon(sourceFolder.isTestSource(), expanded);
        currentRoot = f;
      }
    }

    return originalIcon;
  }

}
