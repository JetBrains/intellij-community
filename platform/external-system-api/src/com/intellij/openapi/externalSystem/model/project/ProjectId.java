// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class ProjectId implements ProjectCoordinate, Serializable {
  private final @Nullable String groupId;
  private final @Nullable String artifactId;
  private final @Nullable String version;

  @PropertyMapping({"groupId", "artifactId", "version"})
  public ProjectId(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  @Override
  public @Nullable String getGroupId() {
    return groupId;
  }

  @Override
  public @Nullable String getArtifactId() {
    return artifactId;
  }

  @Override
  public @Nullable String getVersion() {
    return version;
  }

  public @NotNull String getKey() {
    StringBuilder builder = new StringBuilder();

    append(builder, groupId);
    append(builder, artifactId);
    append(builder, version);

    return builder.toString();
  }

  public @NotNull String getDisplayString() {
    return getKey();
  }

  public static void append(StringBuilder builder, String part) {
    if (!builder.isEmpty()) builder.append(':');
    builder.append(part == null ? "<unknown>" : part);
  }

  @Override
  public String toString() {
    return getDisplayString();
  }

  public boolean equals(@Nullable String groupId, @Nullable String artifactId) {
    if (this.artifactId != null ? !this.artifactId.equals(artifactId) : artifactId != null) return false;
    if (this.groupId != null ? !this.groupId.equals(groupId) : groupId != null) return false;
    return true;
  }

  public boolean equals(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    if (!equals(groupId, artifactId)) return false;
    if (this.version != null ? !this.version.equals(version) : version != null) return false;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectId other = (ProjectId)o;
    return equals(other.getGroupId(), other.artifactId, other.version);
  }

  @Override
  public int hashCode() {
    int result;
    result = (groupId != null ? groupId.hashCode() : 0);
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }
}
