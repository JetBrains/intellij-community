package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class DocumentReferenceManager {
  public static DocumentReferenceManager getInstance() {
    return ApplicationManager.getApplication().getComponent(DocumentReferenceManager.class);
  }

  public abstract DocumentReference create(@NotNull Document document);

  public abstract DocumentReference create(@NotNull VirtualFile file);
}
