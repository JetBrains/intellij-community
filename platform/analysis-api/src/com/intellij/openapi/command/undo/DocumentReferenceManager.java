// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class DocumentReferenceManager {
  public static DocumentReferenceManager getInstance() {
    return ApplicationManager.getApplication().getService(DocumentReferenceManager.class);
  }

  @NotNull
  public abstract DocumentReference create(@NotNull Document document);

  @NotNull
  public abstract DocumentReference create(@NotNull VirtualFile file);
}
