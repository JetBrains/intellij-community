package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;

/**
 * @author max
 */
public interface ChangeProvider {
  void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) throws VcsException;

  /**
   * Returns true if the initial unsaved modification of a document should cause dirty scope invalidation
   * for the file corresponding to the document.
   *
   * @return true if document modification should mark the scope as dirty, false otherwise
   */
  boolean isModifiedDocumentTrackingRequired();
}
