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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class UndoRedoAction extends DumbAwareAction {
  public UndoRedoAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext);
    perform(editor, undoManager);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext);
    if (undoManager == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(isAvailable(editor, undoManager));

    Pair<String, String> pair = getActionNameAndDescription(editor, undoManager);

    presentation.setText(pair.first);
    presentation.setDescription(pair.second);
  }

  private static UndoManager getUndoManager(FileEditor editor, DataContext dataContext) {
    if (editor == null) {
      Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      JRootPane rootPane = UIUtil.getRootPane(component);
      JBPopup popup = rootPane != null ? (JBPopup)rootPane.getClientProperty(JBPopup.KEY) : null;
      boolean modalPopup = popup != null && popup.isModalContext();
      boolean modalContext = Boolean.TRUE.equals(PlatformDataKeys.IS_MODAL_CONTEXT.getData(dataContext));
      if (modalPopup || modalContext) {
        return SwingUndoManagerWrapper.fromContext(dataContext);
      }
    }

    Project project = getProject(editor, dataContext);
    return project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
  }

  private static Project getProject(FileEditor editor, DataContext dataContext) {
    Project project;
    if (editor instanceof TextEditor) {
      project = ((TextEditor)editor).getEditor().getProject();
    }
    else {
      project = CommonDataKeys.PROJECT.getData(dataContext);
    }
    return project;
  }

  protected abstract void perform(FileEditor editor, UndoManager undoManager);

  protected abstract boolean isAvailable(FileEditor editor, UndoManager undoManager);

  protected abstract Pair<String, String> getActionNameAndDescription(FileEditor editor, UndoManager undoManager);

  private static class SwingUndoManagerWrapper extends UndoManager{
    private final javax.swing.undo.UndoManager mySwingUndoManager;

    @Nullable
    static UndoManager fromContext(DataContext dataContext) {
      javax.swing.undo.UndoManager swingUndoManager = UIUtil.getUndoManager(PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext));
      return swingUndoManager != null ? new SwingUndoManagerWrapper(swingUndoManager) : null;
    }

    public SwingUndoManagerWrapper(javax.swing.undo.UndoManager swingUndoManager) {
      mySwingUndoManager = swingUndoManager;
    }

    @Override
    public void undoableActionPerformed(@NotNull UndoableAction action) {
    }

    @Override
    public void nonundoableActionPerformed(@NotNull DocumentReference ref, boolean isGlobal) {
    }

    @Override
    public boolean isUndoInProgress() {
      return false;
    }

    @Override
    public boolean isRedoInProgress() {
      return false;
    }

    @Override
    public void undo(@Nullable FileEditor editor) {
       mySwingUndoManager.undo();
    }

    @Override
    public void redo(@Nullable FileEditor editor) {
      mySwingUndoManager.redo();
    }

    @Override
    public boolean isUndoAvailable(@Nullable FileEditor editor) {
      return mySwingUndoManager.canUndo();
    }

    @Override
    public boolean isRedoAvailable(@Nullable FileEditor editor) {
      return mySwingUndoManager.canRedo();
    }

    @NotNull
    @Override
    public Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
      return getUndoOrRedoActionNameAndDescription( true);
    }

    @NotNull
    @Override
    public Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
      return getUndoOrRedoActionNameAndDescription( false);
    }

    @NotNull
    private Pair<String, String> getUndoOrRedoActionNameAndDescription(boolean undo) {
      String command = undo ? "undo" : "redo";
      return Pair.create(
        ActionsBundle.message("action." + command + ".text", "").trim(),
        ActionsBundle.message("action." + command + ".description",
                              ActionsBundle.message("action." + command + ".description.empty")).trim());
    }
  }
}
