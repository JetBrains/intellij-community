// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.jarRepository.RepositoryLibraryDefinition;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import icons.OpenapiIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import javax.swing.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RepositoryLibraryDescription {
  public static final @NotNull @NonNls String LatestVersionId = "LATEST";
  public static final @NotNull @NonNls String LatestVersionDisplayName = "Latest";
  public static final @NotNull @NonNls String ReleaseVersionId = "RELEASE";
  public static final @NotNull @NonNls String DefaultVersionId = ReleaseVersionId;
  public static final @NotNull @NonNls String ReleaseVersionDisplayName = "Release";
  public static final @NotNull @NonNls String SnapshotVersionSuffix = "-SNAPSHOT";

  public static final Icon DEFAULT_ICON = OpenapiIcons.RepositoryLibraryLogo;

  private static volatile Map<String, RepositoryLibraryDescription> ourStaticallyDefinedLibraries;
  
  private final String groupId;
  private final String artifactId;
  private final @NlsContexts.DialogTitle String libraryName;

  public final String jarRemoteRepositoryId;
  public final boolean sha256sumCheckEnabled;

  protected RepositoryLibraryDescription(String groupId, String artifactId, @NlsContexts.DialogTitle String libraryName,
                                         String jarRemoteRepositoryId, boolean sha256sumCheckEnabled) {
    this.groupId = groupId == null? "" : groupId;
    this.artifactId = artifactId == null? "" : artifactId;
    this.libraryName = libraryName == null ? CodeInsightBundle.message("unknown.node.text") : libraryName;
    this.sha256sumCheckEnabled = sha256sumCheckEnabled;
    this.jarRemoteRepositoryId = jarRemoteRepositoryId;
  }

  protected RepositoryLibraryDescription(String groupId, String artifactId, @NlsContexts.DialogTitle String libraryName) {
      this(groupId, artifactId, libraryName, null, false);
  }

  public static @NotNull RepositoryLibraryDescription findDescription(final @Nullable String groupId, final @Nullable String artifactId) {
    return findDescription(groupId, artifactId, null, false);
  }

  public static @NotNull RepositoryLibraryDescription findDescription(final @Nullable String groupId, final @Nullable String artifactId,
                                                                      String bindToRemoteRepository, boolean sha256sumCheckEnabled) {
    if (ourStaticallyDefinedLibraries == null) {
      final HashMap<String, RepositoryLibraryDescription> map = new HashMap<>();
      for (RepositoryLibraryDefinition def : RepositoryLibraryDefinition.EP_NAME.getExtensionList()) {
        final String id = def.groupId + ":" + def.artifactId;
        map.put(id, new RepositoryLibraryDescription(def.groupId, def.artifactId, def.name));
      }
      ourStaticallyDefinedLibraries = Collections.unmodifiableMap(Collections.synchronizedMap(map));
    }
    final @NlsSafe String id = groupId == null && artifactId == null ? CodeInsightBundle.message("unknown.node.text") : groupId + ":" + artifactId;
    final RepositoryLibraryDescription description = ourStaticallyDefinedLibraries.get(id);
    return description != null? description : new RepositoryLibraryDescription(groupId, artifactId, id, bindToRemoteRepository,
                                                                               sha256sumCheckEnabled);
  }

  public static @NotNull RepositoryLibraryDescription findDescription(final @NotNull RepositoryLibraryProperties properties) {
    return findDescription(properties.getGroupId(), properties.getArtifactId(), properties.getJarRepositoryId(),
                           properties.isEnableSha256Checksum());
  }

  public static @NotNull RepositoryLibraryDescription findDescription(final @NotNull JpsMavenRepositoryLibraryDescriptor descriptor) {
    return findDescription(descriptor.getGroupId(), descriptor.getArtifactId(),
                           descriptor.getJarRepositoryId(),
                           descriptor.isVerifySha256Checksum());
  }

  public @NotNull String getGroupId() {
    return groupId;
  }

  public @NotNull String getArtifactId() {
    return artifactId;
  }

  public @NotNull @NlsContexts.DialogTitle String getDisplayName() {
    return libraryName;
  }

  public @NotNull Icon getIcon() {
    if (sha256sumCheckEnabled && jarRemoteRepositoryId != null) {
      return OpenapiIcons.MavenBindChecksum;
    }

    if (sha256sumCheckEnabled) {
      return OpenapiIcons.MavenChecksum;
    }

    if (jarRemoteRepositoryId != null) {
      return OpenapiIcons.MavenBind;
    }

    return OpenapiIcons.RepositoryLibraryLogo;
  }

  public @Nullable DependencyScope getSuggestedScope() {
    return null;
  }

  // One library could have more then one description - for ex. in different plugins
  // In this case heaviest description will be used
  public int getWeight() {
    return 1000;
  }

  public RepositoryLibraryProperties createDefaultProperties() {
    return new RepositoryLibraryProperties(getGroupId(), getArtifactId(), ReleaseVersionId, true, ContainerUtil.emptyList());
  }

  public @NlsSafe String getDisplayName(String version) {
    if (LatestVersionId.equals(version)) {
      version = LatestVersionDisplayName;
    }
    else if (ReleaseVersionId.equals(version)) {
      version = ReleaseVersionDisplayName;
    }
    return getDisplayName() + (version == null ? "" : ":" + version);
  }

  public @NlsSafe String getMavenCoordinates(String version) {
    return getGroupId() + ":" + getArtifactId() + ":" + version;
  }
}
