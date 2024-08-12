// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.SourceRootPropertiesHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.util.Locale;

/**
 * @author Eugene Zhuravlev
 */
public final class ToggleSourcesStateAction<P extends JpsElement> extends ContentEntryEditingAction {
  private final ContentEntryTreeEditor myEntryTreeEditor;
  private final ModuleSourceRootEditHandler<P> myEditHandler;

  public ToggleSourcesStateAction(JTree tree, ContentEntryTreeEditor entryEditor, ModuleSourceRootEditHandler<P> editHandler) {
    super(tree);
    myEntryTreeEditor = entryEditor;
    myEditHandler = editHandler;
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(editHandler.getMarkRootButtonText());
    templatePresentation.setDescription(ProjectBundle.messagePointer("module.toggle.sources.action.description",
                                                              editHandler.getFullRootTypeName().toLowerCase(Locale.getDefault())));
    templatePresentation.setIcon(editHandler.getRootIcon());
  }

  @Override
  public boolean isSelected(final @NotNull AnActionEvent e) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles.length == 0) return false;

    final ContentEntryEditor editor = myEntryTreeEditor.getContentEntryEditor();
    return editor != null && myEditHandler.getRootType().equals(editor.getRootType(selectedFiles[0]));
  }

  @Override
  public void setSelected(final @NotNull AnActionEvent e, final boolean isSelected) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    assert selectedFiles.length != 0;

    final ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    if (contentEntryEditor == null) return;
    
    for (VirtualFile selectedFile : selectedFiles) {
      final SourceFolder sourceFolder = contentEntryEditor.getSourceFolder(selectedFile);
      if (isSelected) {
        JpsModuleSourceRootType<P> type = myEditHandler.getRootType();
        if (sourceFolder == null) { // not marked yet
          P properties = type.createDefaultProperties();
          contentEntryEditor.addSourceFolder(selectedFile, type, properties);
        }
        else if (!type.equals(sourceFolder.getRootType())) {
          P properties;
          if (type.getClass().equals(sourceFolder.getRootType().getClass())) {
            //noinspection unchecked
            properties = SourceRootPropertiesHelper.createPropertiesCopy((P)sourceFolder.getJpsElement().getProperties(), type);
          }
          else {
            properties = type.createDefaultProperties();
          }
          contentEntryEditor.removeSourceFolder(sourceFolder);
          contentEntryEditor.addSourceFolder(selectedFile, type, properties);
        }
      }
      else if (sourceFolder != null) { // already marked
        contentEntryEditor.removeSourceFolder(sourceFolder);
      }
    }
  }
}
