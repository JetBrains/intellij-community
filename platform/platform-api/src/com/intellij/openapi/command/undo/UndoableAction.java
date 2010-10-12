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
package com.intellij.openapi.command.undo;

import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.openapi.command.undo.UndoManager#undoableActionPerformed(UndoableAction) 
 */
public interface UndoableAction {
  void undo() throws UnexpectedUndoException;
  void redo() throws UnexpectedUndoException;

  /**
   * Returns the documents, affected by this action.
   * If the returned value is null, all documents are "affected".
   * The action can be undone if all of its affected documents are either
   * not affected by any of further actions or all of such actions are undone.
   */
  @Nullable
  DocumentReference[] getAffectedDocuments();

  /**
   * Global actions are those, that can be undone not only from the document of the file, but also from the project tree view.
   */
  boolean isGlobal();
}