/*
 * @author max
 */
package com.intellij.openapi.vfs.watcher;

public enum ChangeKind {
  CREATE,
  DELETE,
  STATS,
  CHANGE,
  DIRTY,
  RECDIRTY,
  RESET
}