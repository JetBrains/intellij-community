package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author max
 */
public interface ChangeList {
  Collection<Change> getChanges();

  @NotNull
  String getName();

  String getComment();
}
