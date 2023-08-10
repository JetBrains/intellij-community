// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.download.impl;

import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.facet.frameworks.beans.ArtifactItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.download.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URL;
import java.util.List;

class DownloadableFileServiceImpl extends DownloadableFileService {
  @NotNull
  @Override
  public DownloadableFileDescription createFileDescription(@NotNull String downloadUrl, @NotNull String fileName) {
    return new DownloadableFileDescriptionImpl(downloadUrl, FileUtilRt.getNameWithoutExtension(fileName), FileUtilRt.getExtension(fileName));
  }

  @NotNull
  @Override
  public DownloadableFileSetVersions<DownloadableFileSetDescription> createFileSetVersions(@Nullable String groupId,
                                                                                           URL @NotNull ... localUrls) {
    return new FileSetVersionsFetcherBase<>(groupId, localUrls) {
      @Override
      protected DownloadableFileSetDescription createVersion(Artifact version, List<? extends DownloadableFileDescription> files) {
        return new DownloadableFileSetDescriptionImpl<>(version.getName(), version.getVersion(), files);
      }

      @Override
      protected DownloadableFileDescription createFileDescription(ArtifactItem item, String url, String prefix) {
        return DownloadableFileService.getInstance().createFileDescription(url, item.getName());
      }
    };
  }

  @NotNull
  @Override
  public FileDownloader createDownloader(@NotNull DownloadableFileSetDescription description) {
    return createDownloader(description.getFiles(), description.getName());
  }

  @NotNull
  @Override
  public FileDownloader createDownloader(@NotNull List<? extends DownloadableFileDescription> fileDescriptions,
                                         @NotNull String presentableDownloadName) {
    return new FileDownloaderImpl(fileDescriptions, null, null, presentableDownloadName);
  }

  @Override
  @NotNull
  public FileDownloader createDownloader(final List<? extends DownloadableFileDescription> fileDescriptions,
                                         final @Nullable Project project,
                                         JComponent parent, @NotNull String presentableDownloadName) {
    return new FileDownloaderImpl(fileDescriptions, project, parent, presentableDownloadName);
  }
}