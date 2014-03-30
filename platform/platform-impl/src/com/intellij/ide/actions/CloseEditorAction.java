
/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseEditorAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);

    FileEditorManagerEx editorManager = getEditorManager(project);
    EditorWindow window = e.getData(EditorWindow.DATA_KEY);
    VirtualFile file = null;
    if (window == null) {
      window = editorManager.getActiveWindow().getResult();
      if (window != null) {
        file = window.getSelectedFile();
      }
    }
    else {
      file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    }
    if (file != null) {
      editorManager.closeFile(file, window);
    }
  }

  private static FileEditorManagerEx getEditorManager(Project project) {
    return (FileEditorManagerEx)FileEditorManager.getInstance(project);
  }

  @Override
  public void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace()) || ActionPlaces.EDITOR_TAB_POPUP.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.close"));
    }
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    if (window == null) {
      window = getEditorManager(project).getActiveWindow().getResult();
    }
    presentation.setEnabled(window != null && window.getTabCount() > 0);
  }
}
