/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.jarRepository.RepositoryLibraryDefinition;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.LayeredIcon;
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
import java.util.Objects;

import static org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.JAR_REPOSITORY_ID_NOT_SET;

public class RepositoryLibraryDescription {
  @NotNull @NonNls
  public static final String LatestVersionId = "LATEST";
  @NotNull @NonNls
  public static final String LatestVersionDisplayName = "Latest";
  @NotNull @NonNls
  public static final String ReleaseVersionId = "RELEASE";
  @NotNull @NonNls
  public static final String DefaultVersionId = ReleaseVersionId;
  @NotNull @NonNls
  public static final String ReleaseVersionDisplayName = "Release";
  @NotNull @NonNls
  public static final String SnapshotVersionSuffix = "-SNAPSHOT";

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
      this(groupId, artifactId, libraryName, JAR_REPOSITORY_ID_NOT_SET, false);
  }

  @NotNull
  public static RepositoryLibraryDescription findDescription(@Nullable final String groupId, @Nullable final String artifactId) {
    return findDescription(groupId, artifactId, JAR_REPOSITORY_ID_NOT_SET, false);
  }

  @NotNull
  public static RepositoryLibraryDescription findDescription(@Nullable final String groupId, @Nullable final String artifactId,
                                                             String bindToRemoteRepository, boolean sha256sumCheckEnabled) {
    if (ourStaticallyDefinedLibraries == null) {
      final HashMap<String, RepositoryLibraryDescription> map = new HashMap<>();
      for (RepositoryLibraryDefinition def : RepositoryLibraryDefinition.EP_NAME.getExtensions()) {
        final String id = def.groupId + ":" + def.artifactId;
        map.put(id, new RepositoryLibraryDescription(def.groupId, def.artifactId, def.name));
      }
      ourStaticallyDefinedLibraries = Collections.unmodifiableMap(Collections.synchronizedMap(map));
    }
    @NlsSafe
    final String id = groupId == null && artifactId == null ? CodeInsightBundle.message("unknown.node.text") : groupId + ":" + artifactId;
    final RepositoryLibraryDescription description = ourStaticallyDefinedLibraries.get(id);
    return description != null? description : new RepositoryLibraryDescription(groupId, artifactId, id, bindToRemoteRepository,
                                                                               sha256sumCheckEnabled);
  }

  @NotNull
  public static RepositoryLibraryDescription findDescription(@NotNull final RepositoryLibraryProperties properties) {
    return findDescription(properties.getGroupId(), properties.getArtifactId(), properties.getJarRepositoryId(),
                           properties.isEnableSha256Checksum());
  }

  @NotNull
  public static RepositoryLibraryDescription findDescription(@NotNull final JpsMavenRepositoryLibraryDescriptor descriptor) {
    return findDescription(descriptor.getGroupId(), descriptor.getArtifactId(),
                           descriptor.getJarRepositoryId(),
                           descriptor.isVerifySha256Checksum());
  }

  @NotNull
  public String getGroupId() {
    return groupId;
  }

  @NotNull
  public String getArtifactId() {
    return artifactId;
  }

  @NotNull
  public @NlsContexts.DialogTitle String getDisplayName() {
    return libraryName;
  }

  @NotNull
  public Icon getIcon() {
    LayeredIcon icon = new LayeredIcon(4);
    icon.setIcon(DEFAULT_ICON, 0);
    if (sha256sumCheckEnabled) {
      icon.setIcon(OpenapiIcons.TransparentStub, 1);
    }
    if (!Objects.equals(jarRemoteRepositoryId, JAR_REPOSITORY_ID_NOT_SET)) {
      icon.setIcon(OpenapiIcons.TransparentStub, 3);
    }
    return icon;
  }

  @Nullable
  public DependencyScope getSuggestedScope() {
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
