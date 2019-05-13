/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.download;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Use {@link DownloadableFileService} to create instances of this interface
 *
 * @author nik
 */
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
