/*
 * @author max
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.EventListener;

public interface FileDocumentSynchronizationVetoListener extends EventListener {
  void beforeDocumentSaving(Document document) throws VetoDocumentSavingException;
  void beforeFileContentReload(VirtualFile file, Document document) throws VetoDocumentReloadException;
}