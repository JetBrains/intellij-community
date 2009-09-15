/*
 * @author max
 */
package com.intellij.history;

import com.intellij.openapi.vfs.VirtualFile;

public class DeafLocalHistory extends LocalHistory {
  public byte[] getByteContent(final VirtualFile f, final FileRevisionTimestampComparator c) {
    throw new UnsupportedOperationException();
  }

  public boolean hasUnavailableContent(final VirtualFile f) {
    return false;
  }

  public boolean isUnderControl(final VirtualFile f) {
    return false;
  }

  public Label putSystemLabel(final String name, final int color) {
    return Label.NULL_INSTANCE;
  }

  public Label putUserLabel(final VirtualFile f, final String name) {
    return Label.NULL_INSTANCE;
  }

  public Label putUserLabel(final String name) {
    return Label.NULL_INSTANCE;
  }

  public LocalHistoryAction startAction(final String name) {
    return LocalHistoryAction.NULL;
  }

  public void save() {
  }
}
