/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BasicUndoableAction implements UndoableAction {
  private final DocumentReference[] myRefs;

  public BasicUndoableAction() {
    myRefs = null;
  }

  public BasicUndoableAction(@Nullable DocumentReference... refs) {
    myRefs = refs;
  }

  public BasicUndoableAction(@NotNull Document... docs) {
    myRefs = new DocumentReference[docs.length];
    for (int i = 0; i < docs.length; i++) {
      myRefs[i] = DocumentReferenceManager.getInstance().create(docs[i]);
    }
  }

  public BasicUndoableAction(@NotNull VirtualFile... files) {
    myRefs = new DocumentReference[files.length];
    for (int i = 0; i < files.length; i++) {
      myRefs[i] = DocumentReferenceManager.getInstance().create(files[i]);
    }
  }

  @Override
  public DocumentReference[] getAffectedDocuments() {
    return myRefs;
  }

  @Override
  public boolean isGlobal() {
    return false;
  }
}
