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
package com.intellij.util.download.impl;

import com.intellij.facet.frameworks.LibrariesDownloadAssistant;
import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.facet.frameworks.beans.ArtifactItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.DownloadableFileSetDescription;
import com.intellij.util.download.DownloadableFileSetVersions;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public abstract class FileSetVersionsFetcherBase<F extends DownloadableFileSetDescription> implements DownloadableFileSetVersions<F> {
  private static final Comparator<DownloadableFileSetDescription> VERSIONS_COMPARATOR = new Comparator<DownloadableFileSetDescription>() {
    @Override
    public int compare(DownloadableFileSetDescription o1, DownloadableFileSetDescription o2) {
      return -StringUtil.compareVersionNumbers(o1.getVersionString(), o2.getVersionString());
    }
  };
  protected final String myGroupId;
  private final URL[] myLocalUrls;

  public FileSetVersionsFetcherBase(@NotNull String groupId, @NotNull URL[] localUrls) {
    myLocalUrls = localUrls;
    myGroupId = groupId;
  }

  @Override
  public void fetchVersions(@NotNull final FileSetVersionsCallback<F> callback) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final Artifact[] versions = LibrariesDownloadAssistant.getVersions(myGroupId, myLocalUrls);
        final List<F> result = new ArrayList<F>();
        for (Artifact version : versions) {
          final ArtifactItem[] items = version.getItems();
          final List<DownloadableFileDescription> files = new ArrayList<DownloadableFileDescription>();
          for (ArtifactItem item : items) {
            String url = item.getUrl();
            final String prefix = version.getUrlPrefix();
            if (!url.startsWith("http://") && prefix != null) {
              url = prefix + url;
            }
            files.add(DownloadableFileService.getInstance().createFileDescription(url, item.getName()));
          }
          result.add(createVersion(version, files));
        }
        Collections.sort(result, VERSIONS_COMPARATOR);
        callback.onSuccess(result);
      }
    });
  }

  protected abstract F createVersion(Artifact version, List<DownloadableFileDescription> files);
}
