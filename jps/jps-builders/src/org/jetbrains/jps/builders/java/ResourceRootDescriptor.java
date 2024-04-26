// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

public class ResourceRootDescriptor extends BuildRootDescriptor {
  private final @NotNull File myRoot;
  private final @NotNull ResourcesTarget myTarget;
  private final @NotNull String myPackagePrefix;
  private final @NotNull Set<File> myExcludes;
  protected final FileFilter myFilterForExcludedPatterns;

  /**
   * @deprecated use {@link #ResourceRootDescriptor(File, ResourcesTarget, String, Set, FileFilter)} instead; this method doesn't honor
   * excluded patterns which may be specified for the module.
   */
  @Deprecated(forRemoval = true)
  public ResourceRootDescriptor(@NotNull File root,
                                @NotNull ResourcesTarget target,
                                @NotNull String packagePrefix,
                                @NotNull Set<File> excludes) {
    this(root, target, packagePrefix, excludes, FileFilters.EVERYTHING);
  }

  public ResourceRootDescriptor(@NotNull File root,
                                @NotNull ResourcesTarget target,
                                @NotNull String packagePrefix,
                                @NotNull Set<File> excludes,
                                @NotNull FileFilter filterForExcludedPatterns) {
    myPackagePrefix = packagePrefix;
    myRoot = root;
    myTarget = target;
    myExcludes = excludes;
    myFilterForExcludedPatterns = filterForExcludedPatterns;
  }

  @Override
  public @NotNull File getRootFile() {
    return myRoot;
  }

  @Override
  public @NotNull Set<File> getExcludedRoots() {
    return myExcludes;
  }

  @Override
  public @NotNull ResourcesTarget getTarget() {
    return myTarget;
  }

  public @NotNull String getPackagePrefix() {
    return myPackagePrefix;
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    final JpsProject project = getTarget().getModule().getProject();
    final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).getCompilerExcludes();
    return file -> !excludes.isExcluded(file) && myFilterForExcludedPatterns.accept(file);
  }

  @Override
  public String toString() {
    return "ResourceRootDescriptor{target='" + myTarget + '\'' + ", root=" + myRoot + '}';
  }

  @Override
  public boolean canUseFileCache() {
    return true;
  }

  @Override
  public @NotNull String getRootId() {
    return FileUtil.toSystemIndependentName(myRoot.getPath());
  }
}
