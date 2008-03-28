package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FileDownloadingAdapter implements FileDownloadingListener {
  public void fileDownloaded(final VirtualFile localFile) {
  }

  public void errorOccured(@NotNull final String errorMessage) {
  }

  public void progressMessageChanged(final boolean indeterminate, @NotNull final String message) {
  }

  public void progressFractionChanged(final double fraction) {
  }
}
