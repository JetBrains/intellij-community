// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public final class ProxyUndoRedoAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification {
  private final @NotNull UndoManager myUndoManager;
  private final @NotNull TextEditor myEditor;
  private final boolean myUndo;

  private ProxyUndoRedoAction(@NotNull UndoManager manager, @NotNull TextEditor editor, boolean undo) {
    ActionUtil.copyFrom(this, undo ? IdeActions.ACTION_UNDO : IdeActions.ACTION_REDO);
    myUndoManager = manager;
    myEditor = editor;
    myUndo = undo;
  }

  public static void register(@Nullable Project project, @NotNull Editor editor, @NotNull JComponent component) {
    UndoManager undoManager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    if (undoManager != null) {
      DiffUtil.registerAction(new ProxyUndoRedoAction(undoManager, textEditor, true), component);
      DiffUtil.registerAction(new ProxyUndoRedoAction(undoManager, textEditor, false), component);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  /**
   * Forces backend only undo for diff editors.
   * It is a hot fix for IJPL-191994.
   * To support speculative (frontend) undo,
   * it is necessary to find a proper fileEditorId that is not obvious in case of diff editors.
   */
  @Override
  public @NotNull ActionRemoteBehavior getBehavior() {
    return ActionRemoteBehavior.BackendOnly;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myUndo ? myUndoManager.isUndoAvailable(myEditor) : myUndoManager.isRedoAvailable(myEditor));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myUndo) {
      myUndoManager.undo(myEditor);
    }
    else {
      myUndoManager.redo(myEditor);
    }
  }
}
