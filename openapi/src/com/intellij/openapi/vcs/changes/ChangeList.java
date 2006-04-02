package com.intellij.openapi.vcs.changes;

import java.util.Collection;

/**
 * @author max
 */
public interface ChangeList {
  Collection<Change> getChanges();

  String getName();

  String getComment();
}
