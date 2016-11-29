/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UndoManager {
  public static final Key<Document> ORIGINAL_DOCUMENT = new Key<>("ORIGINAL_DOCUMENT");

  public static UndoManager getInstance(@NotNull Project project) {
    return project.getComponent(UndoManager.class);
  }

  public static UndoManager getGlobalInstance() {
    return ApplicationManager.getApplication().getComponent(UndoManager.class);
  }

  public abstract void undoableActionPerformed(@NotNull UndoableAction action);
  public abstract void nonundoableActionPerformed(@NotNull DocumentReference ref, boolean isGlobal);

  public abstract boolean isUndoInProgress();
  public abstract boolean isRedoInProgress();

  public abstract void undo(@Nullable FileEditor editor);
  public abstract void redo(@Nullable FileEditor editor);
  public abstract boolean isUndoAvailable(@Nullable FileEditor editor);
  public abstract boolean isRedoAvailable(@Nullable FileEditor editor);

  @NotNull
  public abstract Pair<String, String> getUndoActionNameAndDescription(FileEditor editor);
  @NotNull
  public abstract Pair<String, String> getRedoActionNameAndDescription(FileEditor editor);
}