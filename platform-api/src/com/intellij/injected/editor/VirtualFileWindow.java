/*
 * @author max
 */
package com.intellij.injected.editor;

import com.intellij.openapi.vfs.VirtualFile;

public interface VirtualFileWindow {
  VirtualFile getDelegate();
  DocumentWindow getDocumentWindow();
  boolean isValid();
}
