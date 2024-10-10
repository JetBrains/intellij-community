// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Set;

public class JavaSourceRootDescriptor extends BuildRootDescriptor {
  private final FileFilter myFilterForExcludedPatterns;
  public final @NotNull File root;
  // absolute and normalized
  public final @NotNull Path rootFile;
  public final @NotNull ModuleBuildTarget target;
  public final boolean isGeneratedSources;
  public final boolean isTemp;
  private final String myPackagePrefix;
  private final Set<Path> myExcludes;

  /**
   * @deprecated use {@link #JavaSourceRootDescriptor(File, ModuleBuildTarget, boolean, boolean, String, Set, FileFilter)} instead;
   * this constructor method doesn't honor excluded patterns which may be specified for the module.
   */
  @ApiStatus.Internal
  @Deprecated
  public JavaSourceRootDescriptor(@NotNull File root,
                                  @NotNull ModuleBuildTarget target,
                                  boolean isGenerated,
                                  boolean isTemp,
                                  @NotNull String packagePrefix,
                                  @NotNull Set<File> excludes) {
    this(root, target, isGenerated, isTemp, packagePrefix, convertExcludes(excludes), FileFilters.EVERYTHING, true);
  }

  public static @NotNull JavaSourceRootDescriptor createJavaSourceRootDescriptor(
    @NotNull File root,
    @NotNull ModuleBuildTarget target,
    boolean isGenerated,
    boolean isTemp,
    @NotNull String packagePrefix,
    @NotNull Set<Path> excludes,
    @NotNull FileFilter filterForExcludedPatterns) {
    return new JavaSourceRootDescriptor(root, target, isGenerated, isTemp, packagePrefix, excludes, filterForExcludedPatterns, true);
  }

  public JavaSourceRootDescriptor(@NotNull File root,
                                  @NotNull ModuleBuildTarget target,
                                  boolean isGenerated,
                                  boolean isTemp,
                                  @NotNull String packagePrefix,
                                  @NotNull Set<File> excludes,
                                  @NotNull FileFilter filterForExcludedPatterns) {
    this(root, target, isGenerated, isTemp, packagePrefix, convertExcludes(excludes), filterForExcludedPatterns, true);
  }

  private static Set<Path> convertExcludes(@NotNull Set<File> excludes) {
    Set<Path> result = FileCollectionFactory.createCanonicalPathSet();
    for (File exclude : excludes) {
      result.add(exclude.toPath());
    }
    return result;
  }

  private JavaSourceRootDescriptor(@NotNull File root,
                                   @NotNull ModuleBuildTarget target,
                                   boolean isGenerated,
                                   boolean isTemp,
                                   @NotNull String packagePrefix,
                                   @NotNull Set<Path> excludes,
                                   @NotNull FileFilter filterForExcludedPatterns,
                                   boolean ignored) {
    this.root = root;
    rootFile = root.toPath().toAbsolutePath().normalize();
    this.target = target;
    this.isGeneratedSources = isGenerated;
    this.isTemp = isTemp;
    myPackagePrefix = packagePrefix;
    myExcludes = excludes;
    myFilterForExcludedPatterns = filterForExcludedPatterns;
  }

  @Override
  public String toString() {
    return "RootDescriptor{" +
           "target='" + target + '\'' +
           ", root=" + rootFile +
           ", generated=" + isGeneratedSources +
           '}';
  }

  @Override
  public @NotNull Set<Path> getExcludedRoots() {
    return myExcludes;
  }

  public @NotNull String getPackagePrefix() {
    return myPackagePrefix;
  }

  @Override
  public @NotNull String getRootId() {
    return FileUtilRt.toSystemIndependentName(rootFile.toString());
  }

  @Override
  public @NotNull File getRootFile() {
    return root;
  }

  @Override
  public @NotNull ModuleBuildTarget getTarget() {
    return target;
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getCompilerConfiguration(target.getModule().getProject()).getCompilerExcludes();
    final FileFilter baseFilter = BuilderRegistry.getInstance().getModuleBuilderFileFilter();
    final JavadocSnippetsSkipFilter snippetsSkipFilter = new JavadocSnippetsSkipFilter(getRootFile());
    return file -> baseFilter.accept(file) && !excludes.isExcluded(file) && snippetsSkipFilter.accept(file) && myFilterForExcludedPatterns.accept(file);
  }

  @Override
  public boolean isGenerated() {
    return isGeneratedSources;
  }

  @Override
  public boolean canUseFileCache() {
    return true;
  }
}
