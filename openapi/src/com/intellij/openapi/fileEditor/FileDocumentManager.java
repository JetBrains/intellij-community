/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;

public abstract class FileDocumentManager {
  public static FileDocumentManager getInstance() {
    return ApplicationManager.getApplication().getComponent(FileDocumentManager.class);
  }

  public abstract Document getDocument(VirtualFile file);
  public abstract Document getCachedDocument(VirtualFile file);

  public abstract VirtualFile getFile(Document document);

  public abstract void saveAllDocuments();
  public abstract void saveDocument(Document document);
  public abstract Document[] getUnsavedDocuments();
  public abstract boolean isDocumentUnsaved(Document document);

  public abstract void addFileDocumentManagerListener(FileDocumentManagerListener listener);
  public abstract void removeFileDocumentManagerListener(FileDocumentManagerListener listener);
  public abstract void dispatchPendingEvents(FileDocumentManagerListener listener);

  public abstract void reloadFromDisk(Document document);

  public abstract String getLineSeparator(VirtualFile file, Project project);
}
