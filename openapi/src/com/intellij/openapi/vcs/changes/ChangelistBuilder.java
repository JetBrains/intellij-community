package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public interface ChangelistBuilder {
  void processChange(Change change);
  void processUnversionedFile(VirtualFile file);
}
