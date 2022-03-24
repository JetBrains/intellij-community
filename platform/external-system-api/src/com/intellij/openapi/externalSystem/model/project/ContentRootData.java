// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public final class ContentRootData extends AbstractExternalEntityData {
  @NotNull private final Map<ExternalSystemSourceType, Collection<SourceRoot>> data = new HashMap<>();

  @NotNull private final String rootPath;

  /**
   * Creates new {@code GradleContentRootImpl} object.
   *
   * @param rootPath  path to the root directory
   */
  @PropertyMapping({"owner", "rootPath"})
  public ContentRootData(@NotNull ProjectSystemId owner, @NotNull String rootPath) {
    super(owner);
    this.rootPath = ExternalSystemApiUtil.toCanonicalPath(rootPath);
  }

  /**
   * @param type      target dir type
   * @return          directories of the target type configured for the current content root
   */
  @NotNull
  public Collection<SourceRoot> getPaths(@NotNull ExternalSystemSourceType type) {
    final Collection<SourceRoot> result = data.get(type);
    return result == null ? Collections.emptyList() : result;
  }

  public void storePath(@NotNull ExternalSystemSourceType type, @NotNull String path) throws IllegalArgumentException {
    storePath(type, path, null);
  }

  /**
   * Ask to remember that directory at the given path contains sources of the given type.
   *
   * @param type           target sources type
   * @param path           target source directory path
   * @param packagePrefix  target source directory package prefix
   * @throws IllegalArgumentException   if given path points to the directory that is not located
   *                                    under the {@link #getRootPath() content root}
   */
  public void storePath(@NotNull ExternalSystemSourceType type, @NotNull String path, @Nullable String packagePrefix) throws IllegalArgumentException {
    if (FileUtil.isAncestor(getRootPath(), path, false)) {
      Collection<SourceRoot> paths = data.get(type);
      if (paths == null) {
        data.put(type, paths = new TreeSet<>(SourceRootComparator.INSTANCE));
      }
      paths.add(new SourceRoot(
        ExternalSystemApiUtil.toCanonicalPath(path),
        StringUtil.nullize(packagePrefix, true)
      ));
      return;
    }
    if (!ExternalSystemSourceType.EXCLUDED.equals(type)) { // There are external systems which mark output directory as 'excluded' path.
      // We don't need to bother if it's outside a module content root then.
      throw new IllegalArgumentException(String.format(
        "Can't register given path of type '%s' because it's out of content root.%nContent root: '%s'%nGiven path: '%s'",
        type, getRootPath(), new File(path).getAbsolutePath()
      ));
    }
  }

  @NotNull
  public String getRootPath() {
    return rootPath;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder("content root:");
    for (Map.Entry<ExternalSystemSourceType, Collection<SourceRoot>> entry : data.entrySet()) {
      buffer.append(StringUtil.toLowerCase(entry.getKey().toString())).append("=").append(entry.getValue()).append("|");
    }
    if (!data.isEmpty()) {
      buffer.setLength(buffer.length() - 1);
    }
    return buffer.toString();
  }

  public static class SourceRoot implements Serializable {
    @NotNull
    private final String path;

    @Nullable
    private final String packagePrefix;

    public SourceRoot(@NotNull String path, @Nullable String prefix) {
      this.path = path;
      packagePrefix = prefix;
    }

    @SuppressWarnings("unused")
    private SourceRoot() {
      path = "";
      packagePrefix = "";
    }

    @NotNull
    public String getPath() {
      return path;
    }

    @Nullable
    public String getPackagePrefix() {
      return packagePrefix;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SourceRoot)) return false;
      SourceRoot root = (SourceRoot)o;
      if (packagePrefix != null ? !packagePrefix.equals(root.packagePrefix) : root.packagePrefix != null) return false;
      if (!path.equals(root.path)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = path.hashCode();
      result = 31 * result + (packagePrefix != null ? packagePrefix.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder buffer = new StringBuilder("source_root(");
      buffer.append(path);
      if (packagePrefix != null) {
        buffer.append(", ").append(packagePrefix);
      }
      buffer.append(")");
      return buffer.toString();
    }
  }

  private static final class SourceRootComparator implements Comparator<SourceRoot>, Serializable {
    private static final SourceRootComparator INSTANCE = new SourceRootComparator();

    @Override
    public int compare(@NotNull SourceRoot o1, @NotNull SourceRoot o2) {
      return StringUtil.naturalCompare(o1.path, o2.path);
    }
  }
}
