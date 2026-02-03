// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;
import java.util.Set;

public class ContentEntryTreeCellRenderer extends NodeRenderer {
  protected final ContentEntryTreeEditor myTreeEditor;
  private final List<? extends ModuleSourceRootEditHandler<?>> myEditHandlers;
  private final @NotNull Set<String> myExcludedUrls;

  public ContentEntryTreeCellRenderer(final @NotNull ContentEntryTreeEditor treeEditor,
                                      @NotNull ContentEntry contentEntry,
                                      List<? extends ModuleSourceRootEditHandler<?>> editHandlers) {
    myTreeEditor = treeEditor;
    myEditHandlers = editHandlers;
    myExcludedUrls = ContentEntryEditor.getEntryExcludedUrls(myTreeEditor.getProject(), contentEntry);
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

    final ContentEntryEditor editor = myTreeEditor.getContentEntryEditor();
    if (editor != null) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof NodeDescriptor) {
        final Object element = ((NodeDescriptor<?>)userObject).getElement();
        if (element instanceof FileElement) {
          final VirtualFile file = ((FileElement)element).getFile();
          if (file != null && file.isDirectory()) {
            final ContentEntry contentEntry = editor.getContentEntry();
            if (contentEntry != null) {
              final String prefix = getPresentablePrefix(contentEntry, file);
              if (!prefix.isEmpty()) {
                append(" (" + prefix + ")", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
              }
              setIcon(updateIcon(contentEntry, file, getIcon()));
            }
          }
        }
      }
    }
  }

  private static @NlsSafe String getPresentablePrefix(final ContentEntry entry, final VirtualFile file) {
    for (final SourceFolder sourceFolder : entry.getSourceFolders()) {
      if (file.equals(sourceFolder.getFile())) {
        JpsModuleSourceRoot element = sourceFolder.getJpsElement();
        JavaSourceRootProperties properties = element.getProperties(JavaModuleSourceRootTypes.SOURCES);
        if (properties != null) return properties.getPackagePrefix();
        JavaResourceRootProperties resourceRootProperties = element.getProperties(JavaModuleSourceRootTypes.RESOURCES);
        if (resourceRootProperties != null) return resourceRootProperties.getRelativeOutputPath();
      }
    }
    return "";
  }

  protected Icon updateIcon(final ContentEntry entry, final VirtualFile file, Icon originalIcon) {
    if (ContentEntryEditor.isExcludedOrUnderExcludedDirectory(entry, myExcludedUrls, file)) {
      return AllIcons.Modules.ExcludeRoot;
    }

    final SourceFolder[] sourceFolders = entry.getSourceFolders();
    for (SourceFolder sourceFolder : sourceFolders) {
      if (file.equals(sourceFolder.getFile())) {
        return SourceRootPresentation.getSourceRootIcon(sourceFolder);
      }
    }

    Icon icon = originalIcon;
    VirtualFile currentRoot = null;
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile sourcePath = sourceFolder.getFile();
      if (sourcePath != null && VfsUtilCore.isAncestor(sourcePath, file, true)) {
        if (currentRoot != null && VfsUtilCore.isAncestor(sourcePath, currentRoot, false)) {
          continue;
        }
        Icon folderIcon = getSourceFolderIcon(sourceFolder.getRootType());
        if (folderIcon != null) {
          icon = folderIcon;
        }
        currentRoot = sourcePath;
      }
    }
    return icon;
  }

  private @Nullable Icon getSourceFolderIcon(JpsModuleSourceRootType<?> type) {
    for (ModuleSourceRootEditHandler<?> handler : myEditHandlers) {
      if (handler.getRootType().equals(type)) {
        return handler.getFolderUnderRootIcon();
      }
    }
    return null;
  }
}
