// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public final class ToggleExcludedStateAction extends ContentEntryEditingAction {
  private final ContentEntryTreeEditor myEntryTreeEditor;

  public ToggleExcludedStateAction(JTree tree, ContentEntryTreeEditor entryEditor) {
    super(tree);
    myEntryTreeEditor = entryEditor;
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(ProjectBundle.messagePointer("module.toggle.excluded.action"));
    templatePresentation.setDescription(ProjectBundle.messagePointer("module.toggle.excluded.action.description"));
    templatePresentation.setIcon(AllIcons.Modules.ExcludeRoot);
  }

  @Override
  public boolean isSelected(final @NotNull AnActionEvent e) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles.length == 0) return false;

    ContentEntryEditor editor = myEntryTreeEditor.getContentEntryEditor();
    return editor != null && editor.isExcludedOrUnderExcludedDirectory(selectedFiles[0]);
  }

  @Override
  public void setSelected(final @NotNull AnActionEvent e, final boolean isSelected) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    assert selectedFiles.length != 0;

    ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    if (contentEntryEditor == null) return;
    
    for (VirtualFile selectedFile : selectedFiles) {
      if (isSelected) {
        if (!contentEntryEditor.isExcludedOrUnderExcludedDirectory(selectedFile)) { // not excluded yet
          contentEntryEditor.addExcludeFolder(selectedFile);
        }
      }
      else {
        contentEntryEditor.removeExcludeFolder(selectedFile.getUrl());
      }
    }
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setText(ProjectBundle.messagePointer("module.toggle.excluded.action"));
  }
}
