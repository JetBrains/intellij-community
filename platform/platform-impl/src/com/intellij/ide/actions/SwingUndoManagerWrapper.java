// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.SwingUndoUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Introduced within IJPL-141287 can't undo in `Find in Path` search text field
 */
final class SwingUndoManagerWrapper extends UndoManager {
  private final javax.swing.undo.UndoManager mySwingUndoManager;

  static @Nullable UndoManager fromContext(DataContext dataContext) {
    javax.swing.undo.UndoManager swingUndoManager = SwingUndoUtil.getUndoManager(
      PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    );
    return swingUndoManager != null ? new SwingUndoManagerWrapper(swingUndoManager) : null;
  }

  SwingUndoManagerWrapper(javax.swing.undo.UndoManager swingUndoManager) {
    mySwingUndoManager = swingUndoManager;
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
  public void undo(@Nullable FileEditor editor) {
    mySwingUndoManager.undo();
  }

  @Override
  public void redo(@Nullable FileEditor editor) {
    mySwingUndoManager.redo();
  }

  @Override
  public @NotNull Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(true);
  }

  @Override
  public @NotNull Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(false);
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
        ActionsBundle.message("action.undo.description", ActionsBundle.message("action.undo.description.empty")).trim()
      );
    } else {
      return Pair.create(
        ActionsBundle.message("action.redo.text", "").trim(),
        ActionsBundle.message("action.redo.description", ActionsBundle.message("action.redo.description.empty")).trim()
      );
    }
  }
}
