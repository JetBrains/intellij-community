/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class ProjectId implements Serializable, ProjectCoordinate {
  public static final String UNKNOWN_VALUE = "Unknown";

  @Nullable private final String myGroupId;
  @Nullable private final String myArtifactId;
  @Nullable private final String myVersion;

  public ProjectId(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
  }

  @Nullable
  public String getGroupId() {
    return myGroupId;
  }

  @Nullable
  public String getArtifactId() {
    return myArtifactId;
  }

  @Nullable
  public String getVersion() {
    return myVersion;
  }

  @NotNull
  public String getKey() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myVersion);

    return builder.toString();
  }

  @NotNull
  public String getDisplayString() {
    return getKey();
  }

  public static void append(StringBuilder builder, String part) {
    if (builder.length() != 0) builder.append(':');
    builder.append(part == null ? "<unknown>" : part);
  }

  @Override
  public String toString() {
    return getDisplayString();
  }

  public boolean equals(@Nullable String groupId, @Nullable String artifactId) {
    if (myArtifactId != null ? !myArtifactId.equals(artifactId) : artifactId != null) return false;
    if (myGroupId != null ? !myGroupId.equals(groupId) : groupId != null) return false;
    return true;
  }

  public boolean equals(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    if (!equals(groupId, artifactId)) return false;
    if (myVersion != null ? !myVersion.equals(version) : version != null) return false;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectId other = (ProjectId)o;
    return equals(other.getGroupId(), other.myArtifactId, other.myVersion);
  }

  @Override
  public int hashCode() {
    int result;
    result = (myGroupId != null ? myGroupId.hashCode() : 0);
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }
}
