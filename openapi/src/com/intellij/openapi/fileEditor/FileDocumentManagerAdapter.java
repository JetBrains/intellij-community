/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class FileDocumentManagerAdapter implements FileDocumentManagerListener{
  public void beforeDocumentSaving(Document document) throws VetoDocumentSavingException {
  }

  public void fileWithNoDocumentChanged(VirtualFile file) {
  }

  public void beforeFileContentReload(VirtualFile file, Document document) throws VetoDocumentReloadException {
  }

  public void fileContentReloaded(VirtualFile file, Document document) {
  }

  public void fileContentLoaded(VirtualFile file, Document document) {
  }
}
