// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class GlobalUndoableAction extends BasicUndoableAction {
  public GlobalUndoableAction() {
  }

  public GlobalUndoableAction(DocumentReference... refs) {
    super(refs);
  }

  public GlobalUndoableAction(Document @NotNull ... docs) {
    super(docs);
  }

  public GlobalUndoableAction(VirtualFile @NotNull ... files) {
    super(files);
  }

  @Override
  public boolean isGlobal() {
    return true;
  }
}
