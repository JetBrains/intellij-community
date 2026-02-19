// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.download;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.List;

@ApiStatus.NonExtendable
public abstract class DownloadableFileService {
  public static DownloadableFileService getInstance() {
    return ApplicationManager.getApplication().getService(DownloadableFileService.class);
  }

  public abstract @NotNull DownloadableFileDescription createFileDescription(@NotNull String downloadUrl, @NotNull String fileName);

  /**
   * Create descriptor for set of files
   * @param groupId id of the file set descriptors on <a href="http://frameworks.jetbrains.com/">frameworks site</a>
   * @param localUrls URLs of local copies of the descriptors
   * @return {@link DownloadableFileSetVersions} instance
   */
  public abstract @NotNull DownloadableFileSetVersions<DownloadableFileSetDescription> createFileSetVersions(@Nullable String groupId,
                                                                                                    URL @NotNull ... localUrls);

  public abstract @NotNull FileDownloader createDownloader(@NotNull DownloadableFileSetDescription description);

  public abstract @NotNull FileDownloader createDownloader(@NotNull List<? extends DownloadableFileDescription> fileDescriptions, @NotNull String presentableDownloadName);
}