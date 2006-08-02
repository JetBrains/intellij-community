package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public interface ChangelistBuilder {
  void processChange(Change change);
  void processChangeInList(Change change, ChangeList changeList);
  void processUnversionedFile(VirtualFile file);
  void processLocallyDeletedFile(FilePath file);
  void processModifiedWithoutEditing(VirtualFile file);

  boolean isUpdatingUnversionedFiles();
}
