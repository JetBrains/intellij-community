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

import com.intellij.jarRepository.RepositoryLibraryDefinition;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.HashMap;
import icons.OpenapiIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

public class RepositoryLibraryDescription {
  @NotNull
  public static final String LatestVersionId = "LATEST";
  @NotNull
  public static final String LatestVersionDisplayName = "Latest";
  @NotNull
  public static final String ReleaseVersionId = "RELEASE";
  @NotNull
  public static final String DefaultVersionId = ReleaseVersionId;
  @NotNull
  public static final String ReleaseVersionDisplayName = "Release";
  @NotNull
  public static final String SnapshotVersionSuffix = "-SNAPSHOT";

  public static final Icon DEFAULT_ICON = OpenapiIcons.RepositoryLibraryLogo;
  
  private static volatile Map<String, RepositoryLibraryDescription> ourStaticallyDefinedLibraries;
  
  private final String groupId;
  private final String artifactId;
  private final String libraryName;

  protected RepositoryLibraryDescription(String groupId, String artifactId, String libraryName) {
    this.groupId = groupId == null? "" : groupId;
    this.artifactId = artifactId == null? "" : artifactId;
    this.libraryName = libraryName == null? "<unknown>" : libraryName;
  }

  @NotNull
  public static RepositoryLibraryDescription findDescription(@Nullable final String groupId, @Nullable final String artifactId) {
    if (ourStaticallyDefinedLibraries == null) {
      final HashMap<String, RepositoryLibraryDescription> map = new HashMap<>();
      for (RepositoryLibraryDefinition def : RepositoryLibraryDefinition.EP_NAME.getExtensions()) {
        final String id = def.groupId + ":" + def.artifactId;
        map.put(id, new RepositoryLibraryDescription(def.groupId, def.artifactId, def.name));
      }
      ourStaticallyDefinedLibraries = Collections.unmodifiableMap(Collections.synchronizedMap(map));
    }
    final String id = groupId == null && artifactId == null? "<unknown>" : groupId + ":" + artifactId;
    final RepositoryLibraryDescription description = ourStaticallyDefinedLibraries.get(id);
    return description != null? description : new RepositoryLibraryDescription(groupId, artifactId, id);
  }

  @NotNull
  public static RepositoryLibraryDescription findDescription(@NotNull final RepositoryLibraryProperties properties) {
    return findDescription(properties.getGroupId(), properties.getArtifactId());
  }

  @NotNull
  public static RepositoryLibraryDescription findDescription(@NotNull final JpsMavenRepositoryLibraryDescriptor descriptor) {
    return findDescription(descriptor.getGroupId(), descriptor.getArtifactId());
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
  public String getDisplayName() {
    return libraryName;
  }

  @NotNull
  public Icon getIcon() {
    return DEFAULT_ICON;
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
    return new RepositoryLibraryProperties(getGroupId(), getArtifactId(), ReleaseVersionId, true);
  }

  public String getDisplayName(String version) {
    if (version.equals(LatestVersionId)) {
      version = LatestVersionDisplayName;
    }
    else if (version.equals(ReleaseVersionId)) {
      version = ReleaseVersionDisplayName;
    }
    return getDisplayName() + ":" + version;
  }

  public String getMavenCoordinates(String version) {
    return getGroupId() + ":" + getArtifactId() + ":" + version;
  }
}
