// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.download;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Use {@link DownloadableFileService} to create instances of this interface
 */
@ApiStatus.NonExtendable
public interface FileDownloader {
  /**
   * Same as {@link #downloadWithProgress}, but async and with background progress.
   */
  @NotNull
  CompletableFuture<@Nullable List<Pair<VirtualFile, DownloadableFileDescription>>> downloadWithBackgroundProgress(@Nullable String targetDirectoryPath, @Nullable Project project);

  /**
   * Download files with modal progress dialog.
   *
   * @param targetDirectoryPath target directory for downloaded files. If {@code null} a file chooser will be shown to select target directory.
   * @param project             project instance used to show the progress window
   * @param parentComponent     parent component for the progress window
   * @return list of downloaded files or {@code null} if the downloading process was failed or canceled
   */
  @Nullable
  List<VirtualFile> downloadFilesWithProgress(@Nullable String targetDirectoryPath,
                                              @Nullable Project project,
                                              @Nullable JComponent parentComponent);

  /**
   * Same as {@link #downloadFilesWithProgress} but returns downloaded files along with corresponding descriptions.
   */
  @Nullable
  List<Pair<VirtualFile, DownloadableFileDescription>> downloadWithProgress(@Nullable String targetDirectoryPath,
                                                                            @Nullable Project project,
                                                                            @Nullable JComponent parentComponent);

  /**
   * Download files synchronously. Call this method under progress only (see {@link com.intellij.openapi.progress.Task}).
   *
   * @param targetDir target directory for downloaded files
   * @return list of downloaded files with their descriptions
   * @throws IOException On errors.
   */
  @NotNull
  List<Pair<File, DownloadableFileDescription>> download(@NotNull File targetDir) throws IOException;
}