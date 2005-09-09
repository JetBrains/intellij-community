package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for selecting a version number valid for a specified file placed under
 * version control.
 *
 * @see com.intellij.openapi.vcs.AbstractVcs#getRevisionSelector() 
 * @since 5.0.2
 */
public interface RevisionSelector {
  /**
   * Shows the UI for selecting the version number and returns the selected
   * version number or null if the selection was cancelled.
   *
   * @param file the file for which the version number is requested.
   * @return the version number or null.
   */
  @Nullable VcsRevisionNumber selectNumber(VirtualFile file); 
}
