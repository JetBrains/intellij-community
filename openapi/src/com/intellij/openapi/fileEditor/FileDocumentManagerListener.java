/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.EventListener;

public interface FileDocumentManagerListener extends EventListener{
  void beforeDocumentSaving(Document document) throws VetoDocumentSavingException;
  void fileWithNoDocumentChanged(VirtualFile file);

  void beforeFileContentReload(VirtualFile file, Document document) throws VetoDocumentReloadException;
  void fileContentReloaded(VirtualFile file, Document document);

  void fileContentLoaded(VirtualFile file, Document document);
}
