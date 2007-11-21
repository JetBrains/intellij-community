/*
 * @author max
 */
package com.intellij.history;

import com.intellij.openapi.vfs.VirtualFile;

public class DeafLocalHistory extends LocalHistory {
  protected byte[] getByteContent(final VirtualFile f, final FileRevisionTimestampComparator c) {
    throw new UnsupportedOperationException();
  }

  protected boolean hasUnavailableContent(final VirtualFile f) {
    return false;
  }

  protected boolean isUnderControl(final VirtualFile f) {
    return false;
  }

  protected Checkpoint putCheckpoint() {
    return Checkpoint.NULL_INSTANCE;
  }

  protected void putSystemLabel(final String name, final int color) {
  }

  protected void putUserLabel(final VirtualFile f, final String name) {
  }

  protected void putUserLabel(final String name) {
  }

  protected LocalHistoryAction startAction(final String name) {
    return LocalHistoryAction.NULL;
  }

  public void save() {
  }
}