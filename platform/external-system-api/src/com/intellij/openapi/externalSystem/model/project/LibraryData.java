// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.PropertyMapping;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LibraryData extends AbstractNamedData implements Named, ProjectCoordinate {
  private static final Interner<String> ourPathInterner = Interner.createWeakInterner();

  private final Map<LibraryPathType, Set<String>> paths = new EnumMap<>(LibraryPathType.class);

  private final boolean unresolved;
  private String group;
  private String artifactId;
  private String version;

  public LibraryData(@NotNull ProjectSystemId owner, @NotNull String name) {
    this(owner, name, false);
  }

  @PropertyMapping({"owner", "externalName", "unresolved"})
  public LibraryData(@NotNull ProjectSystemId owner, @NotNull String externalName, boolean unresolved) {
    super(owner, externalName, externalName.isEmpty() ? "" : owner.getReadableName() + ": " + externalName);

    this.unresolved = unresolved;
  }

  @SuppressWarnings("unused")
  private LibraryData() {
    super(ProjectSystemId.IDE, "");

    unresolved = false;
  }

  @Override
  public @Nullable String getGroupId() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  @Override
  public @Nullable String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  @Override
  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public boolean isUnresolved() {
    return unresolved;
  }

  public @NotNull Set<String> getPaths(@NotNull LibraryPathType type) {
    Set<String> result = paths.get(type);
    return result == null ? Collections.emptySet() : result;
  }

  public void addPath(@NotNull LibraryPathType type, @NotNull String path) {
    Set<String> paths = this.paths.get(type);
    if (paths == null) {
      this.paths.put(type, paths = new LinkedHashSet<>());
    }
    paths.add(ourPathInterner.intern(ExternalSystemApiUtil.toCanonicalPath(path)));
  }

  public void forgetAllPaths() {
    paths.clear();
  }

  @Override
  public int hashCode() {
    int result = paths.hashCode();
    result = 31 * result + super.hashCode();
    result = 31 * result + (unresolved ? 0 : 1);
    result = 31 * result + (group != null ? group.hashCode() : 0);
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    LibraryData that = (LibraryData)o;
    if (group != null ? !group.equals(that.group) : that.group != null) return false;
    if (artifactId != null ? !artifactId.equals(that.artifactId) : that.artifactId != null) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;
    return super.equals(that) && unresolved == that.unresolved && paths.equals(that.paths);
  }

  @Override
  public String toString() {
    String externalName = getExternalName();
    String displayName = StringUtil.isEmpty(externalName) ? paths.toString() : externalName;
    return String.format("library %s%s", displayName, unresolved ? "(unresolved)" : "");
  }
}
