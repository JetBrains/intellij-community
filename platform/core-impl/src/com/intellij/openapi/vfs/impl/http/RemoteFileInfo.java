// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface RemoteFileInfo {
  void addDownloadingListener(@NotNull FileDownloadingListener listener);

  void removeDownloadingListener(@NotNull FileDownloadingListener listener);

  void restartDownloading();

  void startDownloading();

  @NlsContexts.DialogMessage String getErrorMessage();

  VirtualFile getLocalFile();

  RemoteFileState getState();

  void cancelDownloading();
}
