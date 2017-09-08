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

import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 *         Date: 13-Jun-16
 */
public class JpsMavenRepositoryLibraryDescriptor {
  private final String myMavenId;
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;
  private final boolean myIncludeTransitiveDependencies;

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    this(groupId, artifactId, version, true);
  }

  public JpsMavenRepositoryLibraryDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                             boolean includeTransitiveDependencies) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myIncludeTransitiveDependencies = includeTransitiveDependencies;
    myMavenId = groupId + ":" + artifactId + ":" + version;
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId) {
    this(mavenId, true);
  }

  public JpsMavenRepositoryLibraryDescriptor(@Nullable String mavenId, boolean includeTransitiveDependencies) {
    myMavenId = mavenId;
    myIncludeTransitiveDependencies = includeTransitiveDependencies;
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

  public String getVersion() {
    return myVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JpsMavenRepositoryLibraryDescriptor that = (JpsMavenRepositoryLibraryDescriptor)o;
    return Objects.equals(myMavenId, that.myMavenId) && myIncludeTransitiveDependencies == that.myIncludeTransitiveDependencies;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myMavenId) * 31 + (myIncludeTransitiveDependencies ? 1 : 0);
  }

  @Override
  public String toString() {
    return myMavenId != null ? myMavenId : "null";
  }
}
