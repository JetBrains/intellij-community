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
package com.intellij.framework.library.impl;

import com.intellij.facet.frameworks.LibrariesDownloadAssistant;
import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.facet.frameworks.beans.ArtifactItem;
import com.intellij.framework.library.DownloadableFileDescription;
import com.intellij.framework.library.DownloadableLibraryAssistant;
import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class LibraryVersionsFetcher implements DownloadableLibraryDescription {
  private static final Comparator<FrameworkLibraryVersion> VERSIONS_COMPARATOR = new Comparator<FrameworkLibraryVersion>() {
    @Override
    public int compare(FrameworkLibraryVersion o1, FrameworkLibraryVersion o2) {
      return -StringUtil.compareVersionNumbers(o1.getVersionString(), o2.getVersionString());
    }
  };
  private final String myGroupId;
  private final URL[] myLocalUrls;

  public LibraryVersionsFetcher(@NotNull String groupId, @NotNull URL[] localUrls) {
    myGroupId = groupId;
    myLocalUrls = localUrls;
  }

  @Override
  public void fetchLibraryVersions(@NotNull final LibraryVersionsCallback callback) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final Artifact[] versions = LibrariesDownloadAssistant.getVersions(myGroupId, myLocalUrls);
        final List<FrameworkLibraryVersion> result = new ArrayList<FrameworkLibraryVersion>();
        for (Artifact version : versions) {
          final ArtifactItem[] items = version.getItems();
          final List<DownloadableFileDescription> files = new ArrayList<DownloadableFileDescription>();
          for (ArtifactItem item : items) {
            String url = item.getUrl();
            final String prefix = version.getUrlPrefix();
            if (!url.startsWith("http://") && prefix != null) {
              url = prefix + url;
            }
            files.add(DownloadableLibraryAssistant.getInstance().createFileDescription(url, item.getName()));
          }
          result.add(new FrameworkLibraryVersionImpl(version.getVersion(), files, myGroupId, null));
        }
        Collections.sort(result, VERSIONS_COMPARATOR);
        callback.onSuccess(result);
      }
    });
  }
}
