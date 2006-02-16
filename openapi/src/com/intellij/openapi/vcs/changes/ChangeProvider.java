package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;

/**
 * @author max
 */
public interface ChangeProvider {
  void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress);
}
