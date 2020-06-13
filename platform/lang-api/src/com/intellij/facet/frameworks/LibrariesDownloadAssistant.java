// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.frameworks;

import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.facet.frameworks.beans.ArtifactItem;
import com.intellij.facet.frameworks.beans.Artifacts;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.SerializationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LibrariesDownloadAssistant {
  private static final Logger LOG = Logger.getInstance(LibrariesDownloadAssistant.class);

  private LibrariesDownloadAssistant() {
  }

  public static Artifact @NotNull [] getVersions(@NotNull String groupId, URL @NotNull ... localUrls) {
    final Artifact[] versions;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      versions = getDownloadServiceVersions(groupId);
    }
    else {
      versions = null;
    }
    return versions == null ? getVersions(localUrls) : versions;
  }

  private static Artifact @Nullable [] getDownloadServiceVersions(@NotNull String id) {
    final URL url = createVersionsUrl(id);
    if (url == null) return null;
    final Artifacts allArtifacts = deserialize(url);
    return allArtifacts == null ? null : allArtifacts.getArtifacts();
  }

  @Nullable
  private static URL createVersionsUrl(@NotNull String id) {
    final String serviceUrl = LibrariesDownloadConnectionService.getInstance().getServiceUrl();
    if (StringUtil.isNotEmpty(serviceUrl)) {
      try {
        final String url = serviceUrl + "/" + id + "/";
        HttpConfigurable.getInstance().prepareURL(url);

        return new URL(url);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
      catch (IOException e) {
         // no route to host, unknown host, etc.
      }
    }

    return null;
  }

  public static Artifact @NotNull [] getVersions(URL @NotNull ... urls) {
    Set<Artifact> versions = new HashSet<>();
    for (URL url : urls) {
      final Artifacts allArtifacts = deserialize(url);
      if (allArtifacts != null) {
        final Artifact[] vers = allArtifacts.getArtifacts();
        if (vers != null) {
          versions.addAll(Arrays.asList(vers));
        }
      }
    }

    return versions.toArray(Artifact.EMPTY_ARRAY);
  }

  @Nullable
  private static Artifacts deserialize(@Nullable URL url) {
    if (url == null) return null;

    Artifacts allArtifacts = null;
    try {
      allArtifacts = XmlSerializer.deserialize(url, Artifacts.class);
    }
    catch (SerializationException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        LOG.error(e);
      }
    }
    return allArtifacts;
  }

  @Nullable
  public static Artifact findVersion(@NotNull final String versionId, final URL @NotNull ... urls) {
    return findVersion(getVersions(urls), versionId);
  }

  @Nullable
  private static Artifact findVersion(Artifact @Nullable [] versions, @NotNull final String versionId) {
    return versions == null ? null : ContainerUtil.find(versions, springVersion -> versionId.equals(springVersion.getVersion()));
  }

  public static LibraryInfo @NotNull [] getLibraryInfos(@NotNull final URL url, @NotNull final String versionId) {
    final Artifact version = findVersion(getVersions(url), versionId);
    return version != null ? getLibraryInfos(version) : LibraryInfo.EMPTY_ARRAY;
  }

  public static LibraryInfo @NotNull [] getLibraryInfos(@Nullable Artifact version) {
    if (version == null) return LibraryInfo.EMPTY_ARRAY;

    final List<LibraryInfo> infos = convert(version.getUrlPrefix(), version.getItems());

    return infos.toArray(LibraryInfo.EMPTY_ARRAY);
  }

  @NotNull
  private static List<LibraryInfo> convert(final String urlPrefix, ArtifactItem @NotNull [] jars) {
    return ContainerUtil.mapNotNull(jars, artifactItem -> {
      String downloadUrl = artifactItem.getUrl();
      if (urlPrefix != null) {
        if (downloadUrl == null) {
          downloadUrl = artifactItem.getName();
        }
        if (!downloadUrl.startsWith("http://")) {
          downloadUrl = urlPrefix + downloadUrl;
        }
      }
      return new LibraryInfo(artifactItem.getName(), downloadUrl, downloadUrl, artifactItem.getMD5(), artifactItem.getRequiredClasses());
    });
  }
}
