// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo;

import org.jetbrains.annotations.Nullable;

/**
 * @see UndoManager#undoableActionPerformed(UndoableAction)
 */
public interface UndoableAction {
  void undo() throws UnexpectedUndoException;
  void redo() throws UnexpectedUndoException;

  default long getPerformedNanoTime() {
    return 0L;
  }

  default void setPerformedNanoTime(long l) {
  }

  /**
   * Returns the documents, affected by this action.
   * If the returned value is null, all documents are "affected".
   * The action can be undone if all of its affected documents are either
   * not affected by any of further actions or all of such actions are undone.
   */
  DocumentReference @Nullable [] getAffectedDocuments();

  /**
   * Global actions are those, that can be undone not only from the document of the file, but also from the project tree view.
   */
  boolean isGlobal();
}
