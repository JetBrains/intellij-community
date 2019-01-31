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
package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public class JpsMavenRepositoryLibraryDescriptor {
  public static final String DEFAULT_PACKAGING = "jar";
  
  private final String myMavenId;
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;
  private final String myPackaging;
  private final boolean myIncludeTransitiveDependencies;
  private final List<String> myExcludedDependencies;

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    this(groupId, artifactId, version, true, Collections.emptyList());
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             boolean includeTransitiveDependencies, @NotNull List<String> excludedDependencies) {
    this(groupId, artifactId, version, DEFAULT_PACKAGING, includeTransitiveDependencies, excludedDependencies);
  }
  
  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             @NotNull final String packaging, boolean includeTransitiveDependencies, @NotNull List<String> excludedDependencies) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myPackaging = packaging;
    myIncludeTransitiveDependencies = includeTransitiveDependencies;
    myExcludedDependencies = excludedDependencies;
    myMavenId = groupId + ":" + artifactId + ":" + version;
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId) {
    this(mavenId, true, Collections.emptyList());
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             boolean includeTransitiveDependencies) {
    this(groupId, artifactId, version, includeTransitiveDependencies, Collections.emptyList());
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId, boolean includeTransitiveDependencies, List<String> excludedDependencies) {
    this(mavenId, DEFAULT_PACKAGING, includeTransitiveDependencies, excludedDependencies);
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId, @NotNull String packaging, boolean includeTransitiveDependencies, List<String> excludedDependencies) {
    myMavenId = mavenId;
    myIncludeTransitiveDependencies = includeTransitiveDependencies;
    myExcludedDependencies = excludedDependencies;
    myPackaging = packaging;
    if (mavenId == null) {
      myGroupId = myArtifactId = myVersion = null;
    }
    else {
      String[] parts = mavenId.split(":");
      myGroupId = parts.length > 0 ? parts[0] : null;
      myArtifactId = parts.length > 1 ? parts[1] : null;
      myVersion = parts.length > 2 ? parts[2] : null;
    }
  }

  public String getMavenId() {
    return myMavenId;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public boolean isIncludeTransitiveDependencies() {
    return myIncludeTransitiveDependencies;
  }

  /**
   * Returns list of excluded transitive dependencies in {@code "<groupId>:<artifactId>"} format.
   */
  public List<String> getExcludedDependencies() {
    return myExcludedDependencies;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getPackaging() {
    return myPackaging;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JpsMavenRepositoryLibraryDescriptor that = (JpsMavenRepositoryLibraryDescriptor)o;
    return myIncludeTransitiveDependencies == that.myIncludeTransitiveDependencies &&
      myMavenId.equals(that.myMavenId) &&
      myPackaging.equals(that.myPackaging) &&
      myExcludedDependencies.equals(that.myExcludedDependencies);
  }

  public int hashCode() {
    return Objects.hash(myMavenId, myPackaging, myIncludeTransitiveDependencies, myExcludedDependencies);
  }

  @Override
  public String toString() {
    return myMavenId != null ? myMavenId : "null";
  }
}
