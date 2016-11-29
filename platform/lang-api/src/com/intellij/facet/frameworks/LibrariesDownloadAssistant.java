package com.intellij.facet.frameworks;

import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.facet.frameworks.beans.ArtifactItem;
import com.intellij.facet.frameworks.beans.Artifacts;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class LibrariesDownloadAssistant {
  private static final Logger LOG = Logger.getInstance("#com.intellij.LibrariesDownloadAssistant");

  private LibrariesDownloadAssistant() {
  }

  @NotNull
  public static Artifact[] getVersions(@NotNull String groupId, @NotNull URL... localUrls) {
    final Artifact[] versions;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      versions = getDownloadServiceVersions(groupId);
    }
    else {
      versions = null;
    }
    return versions == null ? getVersions(localUrls) : versions;
  }

  @Nullable
  private static Artifact[] getDownloadServiceVersions(@NotNull String id) {
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

  @NotNull
  public static Artifact[] getVersions(@NotNull URL... urls) {
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

    return versions.toArray(new Artifact[versions.size()]);
  }

  @Nullable
  private static Artifacts deserialize(@Nullable URL url) {
    if (url == null) return null;

    Artifacts allArtifacts = null;
    try {
      allArtifacts = XmlSerializer.deserialize(url, Artifacts.class);
    }
    catch (XmlSerializationException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        LOG.error(e);
      }
    }
    return allArtifacts;
  }

  @Nullable
  public static Artifact findVersion(@NotNull final String versionId, @NotNull final URL... urls) {
    return findVersion(getVersions(urls), versionId);
  }

  @Nullable
  private static Artifact findVersion(@Nullable Artifact[] versions, @NotNull final String versionId) {
    return versions == null ? null : ContainerUtil.find(versions, springVersion -> versionId.equals(springVersion.getVersion()));
  }

  @NotNull
  public static LibraryInfo[] getLibraryInfos(@NotNull final URL url, @NotNull final String versionId) {
    final Artifact version = findVersion(getVersions(url), versionId);
    return version != null ? getLibraryInfos(version) : LibraryInfo.EMPTY_ARRAY;
  }

  @NotNull
  public static LibraryInfo[] getLibraryInfos(@Nullable Artifact version) {
    if (version == null) return LibraryInfo.EMPTY_ARRAY;

    final List<LibraryInfo> infos = convert(version.getUrlPrefix(), version.getItems());

    return infos.toArray(new LibraryInfo[infos.size()]);
  }

  @NotNull
  private static List<LibraryInfo> convert(final String urlPrefix, @NotNull ArtifactItem[] jars) {
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
