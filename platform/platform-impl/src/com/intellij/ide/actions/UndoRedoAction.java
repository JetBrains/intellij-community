// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ClientProperty;
import com.intellij.util.ui.SwingUndoUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public abstract class UndoRedoAction extends DumbAwareAction implements LightEditCompatible {
  private static final Logger LOG = Logger.getInstance(UndoRedoAction.class);
  public static final Key<Boolean> IGNORE_SWING_UNDO_MANAGER = new Key<>("IGNORE_SWING_UNDO_MANAGER");

  private boolean myActionInProgress;

  public UndoRedoAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    FileEditor editor = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext, true);

    myActionInProgress = true;
    try {
      perform(editor, undoManager);
    }
    finally {
      myActionInProgress = false;
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    FileEditor editor = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext, false);
    if (undoManager == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(isAvailable(editor, undoManager));

    Pair<@NlsActions.ActionText String, @NlsActions.ActionDescription String> pair = getActionNameAndDescription(editor, undoManager);

    presentation.setText(pair.first);
    presentation.setDescription(pair.second);
  }

  private UndoManager getUndoManager(FileEditor editor, DataContext dataContext, boolean isActionPerformed) {
    Component component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    if (component instanceof JTextComponent && !ClientProperty.isTrue(component, IGNORE_SWING_UNDO_MANAGER)) {
      return SwingUndoManagerWrapper.fromContext(dataContext);
    }
    JRootPane rootPane = null;
    JBPopup popup = null;
    if (editor == null) {
      rootPane = UIUtil.getRootPane(component);
      popup = rootPane != null ? (JBPopup)rootPane.getClientProperty(JBPopup.KEY) : null;
      boolean modalPopup = popup != null && popup.isModalContext();
      boolean modalContext = Boolean.TRUE.equals(PlatformCoreDataKeys.IS_MODAL_CONTEXT.getData(dataContext));
      if (modalPopup || modalContext) {
        return SwingUndoManagerWrapper.fromContext(dataContext);
      }
    }
    if (myActionInProgress && isActionPerformed) {
      LOG.error("Recursive undo invocation attempt, component: " + component + ", fileEditor: " + editor +
                ", rootPane: " + rootPane + ", popup: " + popup);
      return null;
    }

    Project project = getProject(editor, dataContext);
    return project != null && !project.isDefault() ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
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

  protected abstract Pair<@NlsActions.ActionText String, @NlsActions.ActionDescription String> getActionNameAndDescription(FileEditor editor, UndoManager undoManager);

  private static final class SwingUndoManagerWrapper extends UndoManager{
    private final javax.swing.undo.UndoManager mySwingUndoManager;

    static @Nullable UndoManager fromContext(DataContext dataContext) {
      javax.swing.undo.UndoManager swingUndoManager =
        SwingUndoUtil.getUndoManager(PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext));
      return swingUndoManager != null ? new SwingUndoManagerWrapper(swingUndoManager) : null;
    }

    SwingUndoManagerWrapper(javax.swing.undo.UndoManager swingUndoManager) {
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

    @Override
    public @NotNull Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
      return getUndoOrRedoActionNameAndDescription( true);
    }

    @Override
    public @NotNull Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
      return getUndoOrRedoActionNameAndDescription( false);
    }

    @Override
    public long getNextUndoNanoTime(@NotNull FileEditor editor) {
      return -1L;
    }

    @Override
    public long getNextRedoNanoTime(@NotNull FileEditor editor) {
      return -1L;
    }

    @Override
    public boolean isNextUndoAskConfirmation(@NotNull FileEditor editor) {
      return false;
    }

    @Override
    public boolean isNextRedoAskConfirmation(@NotNull FileEditor editor) {
      return false;
    }

    private static @NotNull Pair<String, String> getUndoOrRedoActionNameAndDescription(boolean undo) {
      if (undo) {
        return Pair.create(
          ActionsBundle.message("action.undo.text", "").trim(),
          ActionsBundle.message("action.undo.description",
                                ActionsBundle.message("action.undo.description.empty")).trim());
      }
      else {
        return Pair.create(
          ActionsBundle.message("action.redo.text", "").trim(),
          ActionsBundle.message("action.redo.description",
                                ActionsBundle.message("action.redo.description.empty")).trim());
      }
    }
  }
}