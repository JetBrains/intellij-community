// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
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
import javax.swing.text.JTextComponent;
import java.awt.*;

public abstract class UndoRedoAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(UndoRedoAction.class);

  private boolean myActionInProgress;

  public UndoRedoAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext);

    myActionInProgress = true;
    try {
      perform(editor, undoManager);
    }
    finally {
      myActionInProgress = false;
    }
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

  private UndoManager getUndoManager(FileEditor editor, DataContext dataContext) {
    Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    Editor e = dataContext.getData(CommonDataKeys.EDITOR);
    if (component instanceof JTextComponent && (e == null || component != e.getContentComponent())) {
      return SwingUndoManagerWrapper.fromContext(dataContext);
    }
    JRootPane rootPane = null;
    JBPopup popup = null;
    if (editor == null) {
      rootPane = UIUtil.getRootPane(component);
      popup = rootPane != null ? (JBPopup)rootPane.getClientProperty(JBPopup.KEY) : null;
      boolean modalPopup = popup != null && popup.isModalContext();
      boolean modalContext = Boolean.TRUE.equals(PlatformDataKeys.IS_MODAL_CONTEXT.getData(dataContext));
      if (modalPopup || modalContext) {
        return SwingUndoManagerWrapper.fromContext(dataContext);
      }
    }
    if (myActionInProgress) {
      LOG.error("Recursive undo invocation attempt, component: " + component + ", fileEditor: " + editor + ", editor: " + e +
                ", rootPane: " + rootPane + ", popup: " + popup);
      return null;
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
