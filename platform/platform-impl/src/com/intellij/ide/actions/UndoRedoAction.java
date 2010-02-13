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

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

public abstract class UndoRedoAction extends DumbAwareAction {
  public UndoRedoAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext);
    Project project = getProject(editor, dataContext);

    if (editor == null && project != null) {
      if (DumbService.getInstance(project).isDumb()) {
        DumbService.getInstance(project).showDumbModeNotification("Global Undo and Redo are not available while indices are being built");
        return;
      }
    }
    
    perform(editor, undoManager);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);

    // do not allow global undo in dialogs
    if (editor == null) {
      final Boolean isModalContext = PlatformDataKeys.IS_MODAL_CONTEXT.getData(dataContext);
      if (isModalContext != null && isModalContext) {
        presentation.setEnabled(false);
        return;
      }
    }

    UndoManager undoManager = getUndoManager(editor, dataContext);
    boolean available = isAvailable(editor, undoManager);
    presentation.setEnabled(available);
    String actionName = available ? formatAction(editor, undoManager) : null;
    if (actionName == null) actionName = "";
    String shortActionName = StringUtil.first(actionName, 30, true);
    if (actionName.length() == 0) actionName = ActionsBundle.message(getActionDescriptionEmptyMessageKey());

    presentation.setText(ActionsBundle.message(getActionMessageKey(), shortActionName).trim());
    presentation.setDescription(ActionsBundle.message(getActionDescriptionMessageKey(), actionName).trim());
  }

  private static UndoManager getUndoManager(FileEditor editor, DataContext dataContext) {
    Project project = getProject(editor, dataContext);
    return project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
  }

  private static Project getProject(FileEditor editor, DataContext dataContext) {
    Project project;
    if (editor instanceof TextEditor) {
      project = ((TextEditor)editor).getEditor().getProject();
    }
    else {
      project = PlatformDataKeys.PROJECT.getData(dataContext);
    }
    return project;
  }

  protected abstract boolean isAvailable(FileEditor editor, UndoManager undoManager);

  protected abstract void perform(FileEditor editor, UndoManager undoManager);

  protected abstract String getActionMessageKey();

  protected abstract String getActionDescriptionMessageKey();

  protected abstract String getActionDescriptionEmptyMessageKey();

  @Nullable
  protected abstract String formatAction(FileEditor editor, UndoManager undoManager);
}
