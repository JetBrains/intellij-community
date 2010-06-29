
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseAllEditorsButActiveAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    FileEditorManagerEx fileEditorManager=FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = e.getData(EditorWindow.DATA_KEY);
    if (window != null){
      selectedFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      final VirtualFile[] files = window.getFiles();
      for (final VirtualFile file : files) {
        if (file != selectedFile && !window.isFilePinned(file)) {
          window.closeFile(file);
        }
      }
      return;
    }
    selectedFile = fileEditorManager.getSelectedFiles()[0];
    final VirtualFile[] siblings = fileEditorManager.getSiblings(selectedFile);
    for (final VirtualFile sibling : siblings) {
      if (selectedFile != sibling) {
        fileEditorManager.closeFile(sibling);
      }
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    if (window != null){
      presentation.setEnabled(window.getFiles().length > 1);
      return;
    } else {
      if (fileEditorManager.getSelectedFiles().length == 0) {
        presentation.setEnabled(false);
        return;
      }
      selectedFile = fileEditorManager.getSelectedFiles()[0];
    }
    VirtualFile[] siblings = fileEditorManager.getSiblings(selectedFile);
    presentation.setEnabled(siblings.length > 1);
  }
}
