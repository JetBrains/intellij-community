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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Use {@link DownloadableFileService} to create instances of this interface
 *
 * @author nik
 */
public interface FileDownloader {
  /**
   * Download files with modal progress dialog
   * @param targetDirectoryPath target directory for downloaded files. If {@code null} a file chooser will be shown to select target directory
   * @param project project instance used to show the progress window
   * @param parentComponent parent component for the progress window
   * @return list of downloaded files or {@code null} if the downloading process was failed or cancelled
   */
  @Nullable
  List<VirtualFile> downloadFilesWithProgress(@Nullable String targetDirectoryPath, @Nullable Project project, @Nullable JComponent parentComponent);

  /**
   * Same as {@link #downloadFilesWithProgress} but returns downloaded files along with corresponding descriptions
   */
  @Nullable
  List<Pair<VirtualFile, DownloadableFileDescription>> downloadWithProgress(@Nullable String targetDirectoryPath,
                                                                            @Nullable Project project, @Nullable JComponent parentComponent);

  /**
   * Download files synchronously. Call this method under progress only (see {@link com.intellij.openapi.progress.Task})
   * @param targetDir target directory for downloaded files
   * @return list of downloaded files with their descriptions
   * @throws IOException
   */
  @NotNull
  List<Pair<File, DownloadableFileDescription>> download(@NotNull File targetDir) throws IOException;

  /**
   * @deprecated specify target directory in {@link #downloadWithProgress} or {@link #downloadFilesWithProgress} method instead
   */
  @Deprecated
  @NotNull
  FileDownloader toDirectory(@NotNull String directoryForDownloadedFilesPath);

  /**
   * @deprecated use {@link #downloadFilesWithProgress} instead
   */
  @Deprecated
  @Nullable
  VirtualFile[] download();

  /**
   * @deprecated use {@link #downloadWithProgress} instead
   */
  @Deprecated
  @Nullable
  List<Pair<VirtualFile, DownloadableFileDescription>> downloadAndReturnWithDescriptions();
}
