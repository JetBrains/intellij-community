package com.intellij.facet.frameworks;

import com.intellij.facet.frameworks.actions.GetAllVersionsAction;
import com.intellij.facet.frameworks.actions.GetVersionInfoAction;
import com.intellij.facet.frameworks.beans.DownloadJar;
import com.intellij.facet.frameworks.beans.Version;
import com.intellij.facet.frameworks.beans.Versions;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.XmlSerializer;
import org.apache.commons.codec.binary.Hex;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class LibrariesDownloadAssistant {

  private LibrariesDownloadAssistant() {
  }

  // for local files
  @NotNull
  public static Version[] getVersions(@NotNull URL... urls) {
    Set<Version> versions = new HashSet<Version>();
    for (URL url : urls) {
      final Versions allVersions = XmlSerializer.deserialize(url, Versions.class);
      if (allVersions != null) {
        final Version[] vers = allVersions.getVersions();
        if (vers != null) {
          versions.addAll(Arrays.asList(vers));
        }
      }
    }

    return versions.toArray(new Version[versions.size()]);
  }

  @Nullable
  public static Version[] getVersions(@NotNull String id) {
    final URL url = GetAllVersionsAction.create(id).getUrl();

    if (url == null) return null;

    final Versions allVersions = XmlSerializer.deserialize(url, Versions.class);

    return allVersions == null ? null : allVersions.getVersions();
  }

  @Nullable
  public static Version getVersion(@NotNull String id, @NotNull String versionId) {
    final URL url = GetVersionInfoAction.create(id, versionId).getUrl();
    if (url == null) return null;

    final Versions allVersions = XmlSerializer.deserialize(url, Versions.class);

    if(allVersions == null) return null;

    final Version[] versions = allVersions.getVersions();

    assert versions.length == 1;

    return versions[0];
  }

  @Nullable
  public static Version findVersion(@NotNull final String versionId, @NotNull final URL... urls) {
    return findVersion(getVersions(urls), versionId);
  }

  @Nullable
  public static Version findVersion(@NotNull final String groupId, @NotNull final String versionId) {
    return findVersion(getVersions(groupId), versionId);
  }

  @Nullable
  public static Version findVersion(@Nullable Version[] versions, @NotNull final String versionId) {
    return versions == null ? null : ContainerUtil.find(versions, new Condition<Version>() {
      public boolean value(final Version springVersion) {
        return versionId.equals(springVersion.getId());
      }
    });
  }

  @NotNull
  public static LibraryInfo[] getLibraryInfos(@NotNull final URL url, @NotNull final String versionId) {
    final Version version = findVersion(getVersions(url), versionId);
    return version != null ? getLibraryInfos(version) : LibraryInfo.EMPTY_ARRAY;
  }

  @NotNull
  public static LibraryInfo[] getLibraryInfos(@Nullable Version version) {
    if (version == null) return LibraryInfo.EMPTY_ARRAY;

    final List<LibraryInfo> infos = convert(version.getJars());

    return infos.toArray(new LibraryInfo[infos.size()]);
  }

  @NotNull
  public static List<LibraryInfo> convert(@NotNull DownloadJar[] jars) {
    return ContainerUtil.mapNotNull(jars, new Function<DownloadJar, LibraryInfo>() {
      @Override
      public LibraryInfo fun(DownloadJar downloadJar) {
        final String downloadUrl = downloadJar.getDownloadUrl();
        return new LibraryInfo(downloadJar.getName(), downloadUrl, downloadUrl, downloadJar.getMD5(), downloadJar.getRequiredClasses());
      }
    });
  }
}
