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

package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * @since Oct 14, 2003
 */
public class ToggleSourcesStateAction extends ContentEntryEditingAction {
  private final ContentEntryTreeEditor myEntryTreeEditor;
  private final boolean myEditTestSources;

  public ToggleSourcesStateAction(JTree tree, ContentEntryTreeEditor entryEditor, boolean editTestSources) {
    super(tree);
    myEntryTreeEditor = entryEditor;
    myEditTestSources = editTestSources;
    final Presentation templatePresentation = getTemplatePresentation();
    if (editTestSources) {
      templatePresentation.setText(ProjectBundle.message("module.toggle.test.sources.action"));
      templatePresentation.setDescription(ProjectBundle.message("module.toggle.test.sources.action.description"));
      templatePresentation.setIcon(AllIcons.Modules.TestRoot);
    }
    else {
      templatePresentation.setText(ProjectBundle.message("module.toggle.sources.action"));
      templatePresentation.setDescription(ProjectBundle.message("module.toggle.sources.action.description"));
      templatePresentation.setIcon(AllIcons.Modules.SourceRoot);
    }
  }

  @Override
  public boolean isSelected(final AnActionEvent e) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles.length == 0) return false;

    final ContentEntryEditor editor = myEntryTreeEditor.getContentEntryEditor();
    return myEditTestSources ? editor.isTestSource(selectedFiles[0]) : editor.isSource(selectedFiles[0]);
  }

  @Override
  public void setSelected(final AnActionEvent e, final boolean isSelected) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    assert selectedFiles.length != 0;

    final ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    for (VirtualFile selectedFile : selectedFiles) {
      final SourceFolder sourceFolder = contentEntryEditor.getSourceFolder(selectedFile);
      if (isSelected) {
        if (sourceFolder == null) { // not marked yet
          contentEntryEditor.addSourceFolder(selectedFile, myEditTestSources, "");
        }
        else {
          if (myEditTestSources != sourceFolder.isTestSource()) {
            final String packagePrefix = sourceFolder.getPackagePrefix();
            contentEntryEditor.removeSourceFolder(sourceFolder);
            contentEntryEditor.addSourceFolder(selectedFile, myEditTestSources, packagePrefix);
          }
        }
      }
      else {
        if (sourceFolder != null) { // already marked
          contentEntryEditor.removeSourceFolder(sourceFolder);
        }
      }
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setText(ProjectBundle.message(myEditTestSources ? "module.toggle.test.sources.action" : "module.toggle.sources.action"));
  }
}
