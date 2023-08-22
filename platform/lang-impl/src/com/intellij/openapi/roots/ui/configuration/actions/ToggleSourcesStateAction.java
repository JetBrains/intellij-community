// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;

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
  public boolean isSelected(@NotNull final AnActionEvent e) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles.length == 0) return false;

    final ContentEntryEditor editor = myEntryTreeEditor.getContentEntryEditor();
    return editor != null && myEditHandler.getRootType().equals(editor.getRootType(selectedFiles[0]));
  }

  @Override
  public void setSelected(@NotNull final AnActionEvent e, final boolean isSelected) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    assert selectedFiles.length != 0;

    final ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    if (contentEntryEditor == null) return;
    
    for (VirtualFile selectedFile : selectedFiles) {
      final SourceFolder sourceFolder = contentEntryEditor.getSourceFolder(selectedFile);
      if (isSelected) {
        if (sourceFolder == null) { // not marked yet
          P properties = myEditHandler.getRootType().createDefaultProperties();
          contentEntryEditor.addSourceFolder(selectedFile, myEditHandler.getRootType(), properties);
        }
        else if (!myEditHandler.getRootType().equals(sourceFolder.getRootType())) {
          P properties;
          if (myEditHandler.getRootType().getClass().equals(sourceFolder.getRootType().getClass())) {
            properties = (P)sourceFolder.getJpsElement().getProperties().getBulkModificationSupport().createCopy();
          }
          else {
            properties = myEditHandler.getRootType().createDefaultProperties();
          }
          contentEntryEditor.removeSourceFolder(sourceFolder);
          contentEntryEditor.addSourceFolder(selectedFile, myEditHandler.getRootType(), properties);
        }
      }
      else if (sourceFolder != null) { // already marked
        contentEntryEditor.removeSourceFolder(sourceFolder);
      }
    }
  }
}
