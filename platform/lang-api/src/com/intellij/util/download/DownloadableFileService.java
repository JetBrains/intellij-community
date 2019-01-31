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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URL;
import java.util.List;

/**
 * @author nik
 */
public abstract class DownloadableFileService {
  public static DownloadableFileService getInstance() {
    return ServiceManager.getService(DownloadableFileService.class);
  }

  @NotNull
  public abstract DownloadableFileDescription createFileDescription(@NotNull String downloadUrl, @NotNull String fileName);

  /**
   * Create descriptor for set of files
   * @param groupId id of the file set descriptors on http://frameworks.jetbrains.com/ site
   * @param localUrls URLs of local copies of the descriptors
   * @return {@link DownloadableFileSetVersions} instance
   */
  @NotNull
  public abstract DownloadableFileSetVersions<DownloadableFileSetDescription> createFileSetVersions(@Nullable String groupId,
                                                                                                    @NotNull URL... localUrls);

  @NotNull
  public abstract FileDownloader createDownloader(@NotNull DownloadableFileSetDescription description);

  @NotNull
  public abstract FileDownloader createDownloader(@NotNull List<? extends DownloadableFileDescription> fileDescriptions, @NotNull String presentableDownloadName);

  /**
   * @deprecated use {@link #createDownloader(DownloadableFileSetDescription)} instead
   */
  @Deprecated
  @NotNull
  public abstract FileDownloader createDownloader(@NotNull DownloadableFileSetDescription description, @Nullable Project project,
                                                  JComponent parent);

  /**
   * @deprecated use {@link #createDownloader(java.util.List, String)} instead
   */
  @Deprecated
  @NotNull
  public abstract FileDownloader createDownloader(List<? extends DownloadableFileDescription> fileDescriptions, @Nullable Project project,
                                                  JComponent parent, @NotNull String presentableDownloadName);
}
