package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface ChangelistBuilder {
  void processChange(Change change);
  void processChangeInList(Change change, @Nullable ChangeList changeList);

  /**
   * Put the given change into the change list with the given name.
   * If there is no such change list it is created.
   * This method allows not to refer to ChangeListManager for the LocalChangeList object.
   * @param change Submitted change
   * @param changeListName A name for a change list.
   */
  void processChangeInList(Change change, String changeListName );
  void processUnversionedFile(VirtualFile file);
  void processLocallyDeletedFile(FilePath file);
  void processModifiedWithoutCheckout(VirtualFile file);
  void processIgnoredFile(VirtualFile file);

  /**
   * Report a file which has been updated to a branch other than that of the files around it
   * ("switched"). Changed files (reported through {@link #processChange}) can also be reported as switched.
   *
   * @param file      the switched file
   * @param branch    the name of the branch to which the file is switched.
   * @param recursive if true, all subdirectories of file are also marked as switched to that branch
   */
  void processSwitchedFile(VirtualFile file, String branch, final boolean recursive);

  boolean isUpdatingUnversionedFiles();
}
