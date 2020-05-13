// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.openapi.command.impl.UndoProvider
 */
public abstract class UndoManager {
  public static final Key<Document> ORIGINAL_DOCUMENT = new Key<>("ORIGINAL_DOCUMENT");

  public static UndoManager getInstance(@NotNull Project project) {
    return project.isDefault() ? getGlobalInstance() : project.getComponent(UndoManager.class);
  }

  public static UndoManager getGlobalInstance() {
    return ApplicationManager.getApplication().getComponent(UndoManager.class);
  }

  public abstract void undoableActionPerformed(@NotNull UndoableAction action);

  public abstract void nonundoableActionPerformed(@NotNull DocumentReference ref, boolean isGlobal);

  public abstract boolean isUndoInProgress();

  public abstract boolean isRedoInProgress();

  public boolean isUndoOrRedoInProgress() {
    return isUndoInProgress() || isRedoInProgress();
  }

  public abstract void undo(@Nullable FileEditor editor);

  public abstract void redo(@Nullable FileEditor editor);

  public abstract boolean isUndoAvailable(@Nullable FileEditor editor);

  public abstract boolean isRedoAvailable(@Nullable FileEditor editor);

  @NotNull
  public abstract Pair<String, String> getUndoActionNameAndDescription(FileEditor editor);

  @NotNull
  public abstract Pair<String, String> getRedoActionNameAndDescription(FileEditor editor);
}