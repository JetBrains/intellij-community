package com.intellij.openapi.vcs.changes;

import java.util.Collection;

/**
 * @author max
 */
public interface ChangeProvider {
  Collection<Change> getChanges(final VcsDirtyScope dirtyScope);
}
