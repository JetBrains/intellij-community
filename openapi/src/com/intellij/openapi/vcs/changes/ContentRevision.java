package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface ContentRevision {
  /**
   * Content of the revision. Implementers are encouraged to lazy implement this especially when it requires connection to the
   * version control server or something.
   * Might return null in case if file path denotes a directory or content is impossible to retreive.
   *
   * @return content of the revision
   * @throws com.intellij.openapi.vcs.VcsException in case when content retrieval fails
   */
  @Nullable
  String getContent() throws VcsException;

  /**
   * @return file path of the revision
   */
  @NotNull
  FilePath getFile();

  /**
   * Revision ID. Content revisions with same file path and revision number are considered to be equal and must have same content unless
   * {@link VcsRevisionNumber#NULL} is returned. Use {@link VcsRevisionNumber#NULL} when revision number is not applicable like for
   * the currently uncommited revision.
   * @return revision ID in terms of version control
   */
  @NotNull
  VcsRevisionNumber getRevisionNumber();
}
