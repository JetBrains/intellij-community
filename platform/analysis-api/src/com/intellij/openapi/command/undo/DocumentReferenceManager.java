// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class DocumentReferenceManager {
  public static DocumentReferenceManager getInstance() {
    return ApplicationManager.getApplication().getService(DocumentReferenceManager.class);
  }

  public abstract @NotNull DocumentReference create(@NotNull Document document);

  public abstract @NotNull DocumentReference create(@NotNull VirtualFile file);
}
