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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Use {@link DownloadableFileService} to create instances of this interface
 *
 * @author nik
 */
public interface FileDownloader {
  /**
   * Specifies target directory for downloaded files. If target directory is not specified a file chooser will be shown from {@link #download()} method
   * @param directoryForDownloadedFilesPath target directory path
   * @return the same instance
   */
  @NotNull
  FileDownloader toDirectory(@NotNull String directoryForDownloadedFilesPath);

  /**
   * Download files with modal progress dialog.
   * @return downloaded files
   */
  @Nullable
  VirtualFile[] download();

  /**
   * Download files with modal progress dialog.
   * @return downloaded files with corresponding descriptions
   */
  @Nullable
  List<Pair<VirtualFile, DownloadableFileDescription>> downloadAndReturnWithDescriptions();
}
