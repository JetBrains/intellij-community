// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.download;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Use {@link DownloadableFileService} to create instances of this interface
 */
@ApiStatus.NonExtendable
public interface DownloadableFileSetVersions<F extends DownloadableFileSetDescription> {
  /**
   * Fetches available versions of file sets and calls {@code callback.onSuccess} when finished
   * @param callback callback to receive the result
   */
  void fetchVersions(@NotNull FileSetVersionsCallback<F> callback);

  @NotNull
  List<F> fetchVersions();

  abstract class FileSetVersionsCallback<F extends DownloadableFileSetDescription> {
    public abstract void onSuccess(@NotNull List<? extends F> versions);

    public void onError(@NotNull String errorMessage) {
    }
  }
}