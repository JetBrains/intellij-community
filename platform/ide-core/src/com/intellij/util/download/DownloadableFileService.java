// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public abstract DownloadableFileDescription createFileDescription(@NotNull String downloadUrl, @NotNull String fileName);

  /**
   * Create descriptor for set of files
   * @param groupId id of the file set descriptors on <a href="http://frameworks.jetbrains.com/">frameworks site</a>
   * @param localUrls URLs of local copies of the descriptors
   * @return {@link DownloadableFileSetVersions} instance
   */
  @NotNull
  public abstract DownloadableFileSetVersions<DownloadableFileSetDescription> createFileSetVersions(@Nullable String groupId,
                                                                                                    URL @NotNull ... localUrls);

  @NotNull
  public abstract FileDownloader createDownloader(@NotNull DownloadableFileSetDescription description);

  @NotNull
  public abstract FileDownloader createDownloader(@NotNull List<? extends DownloadableFileDescription> fileDescriptions, @NotNull String presentableDownloadName);
}