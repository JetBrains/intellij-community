package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;

/**
 * @author max
 */
public interface ChangeProvider {
  void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress);

  /**
   * Returns true if the initial unsaved modification of a document should cause dirty scope invalidation
   * for the file corresponding to the document.
   *
   * @return true if document modification should mark the scope as dirty, false otherwise
   */
  boolean isModifiedDocumentTrackingRequired();
}
