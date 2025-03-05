// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
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
  private final FileFilter filterForExcludedPatterns;
  public final @NotNull File root;
  // absolute and normalized
  public final @NotNull Path rootFile;
  public final @NotNull ModuleBuildTarget target;
  public final boolean isGeneratedSources;
  public final boolean isTemp;
  private final String packagePrefix;
  private final Set<Path> excludes;

  /**
   * @deprecated use {@link #createJavaSourceRootDescriptor} instead;
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
    this(root,
         root.toPath().toAbsolutePath().normalize(),
         target,
         isGenerated,
         isTemp,
         packagePrefix,
         convertExcludes(excludes),
         FileFilters.EVERYTHING,
         true);
  }

  public static @NotNull JavaSourceRootDescriptor createJavaSourceRootDescriptor(
    @NotNull File root,
    @NotNull ModuleBuildTarget target,
    boolean isGenerated,
    boolean isTemp,
    @NotNull String packagePrefix,
    @NotNull Set<Path> excludes,
    @NotNull FileFilter filterForExcludedPatterns) {
    return new JavaSourceRootDescriptor(root,
                                        root.toPath().toAbsolutePath().normalize(),
                                        target,
                                        isGenerated,
                                        isTemp,
                                        packagePrefix,
                                        excludes,
                                        filterForExcludedPatterns,
                                        true);
  }

  @ApiStatus.Internal
  public static @NotNull JavaSourceRootDescriptor createJavaSourceRootDescriptor(
    @NotNull Path root,
    @NotNull ModuleBuildTarget target) {
    return new JavaSourceRootDescriptor(root.toFile(),
                                        root,
                                        target,
                                        false,
                                        false,
                                        "",
                                        Set.of(),
                                        FileFilters.EVERYTHING,
                                        true);
  }

  /**
   * @deprecated use {@link #createJavaSourceRootDescriptor} instead;
   */
  @Deprecated
  public JavaSourceRootDescriptor(@NotNull File root,
                                  @NotNull ModuleBuildTarget target,
                                  boolean isGenerated,
                                  boolean isTemp,
                                  @NotNull String packagePrefix,
                                  @NotNull Set<File> excludes,
                                  @NotNull FileFilter filterForExcludedPatterns) {
    this(root,
         root.toPath().toAbsolutePath().normalize(),
         target,
         isGenerated,
         isTemp,
         packagePrefix,
         convertExcludes(excludes),
         filterForExcludedPatterns,
         true);
  }

  private static @Unmodifiable @NotNull Set<Path> convertExcludes(@NotNull Set<File> excludes) {
    Set<Path> result = FileCollectionFactory.createCanonicalPathSet(excludes.size());
    for (File exclude : excludes) {
      result.add(exclude.toPath());
    }
    return result;
  }

  private JavaSourceRootDescriptor(@NotNull File root,
                                   @NotNull Path rootFile,
                                   @NotNull ModuleBuildTarget target,
                                   boolean isGenerated,
                                   boolean isTemp,
                                   @NotNull String packagePrefix,
                                   @NotNull Set<Path> excludes,
                                   @NotNull FileFilter filterForExcludedPatterns,
                                   boolean ignored) {
    this.root = root;
    this.rootFile = rootFile;
    this.target = target;
    this.isGeneratedSources = isGenerated;
    this.isTemp = isTemp;
    this.packagePrefix = packagePrefix;
    this.excludes = excludes;
    this.filterForExcludedPatterns = filterForExcludedPatterns;
  }

  @Override
  public final String toString() {
    return "RootDescriptor{" +
           "target='" + target + '\'' +
           ", root=" + rootFile +
           ", generated=" + isGeneratedSources +
           '}';
  }

  @Override
  public final @NotNull Set<Path> getExcludedRoots() {
    return excludes;
  }

  public final @NotNull String getPackagePrefix() {
    return packagePrefix;
  }

  @Override
  public final @NotNull String getRootId() {
    return FileUtilRt.toSystemIndependentName(rootFile.toString());
  }

  @Override
  public final @NotNull File getRootFile() {
    return root;
  }

  @Override
  public @NotNull Path getFile() {
    return rootFile;
  }

  @Override
  public final @NotNull ModuleBuildTarget getTarget() {
    return target;
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getCompilerConfiguration(target.getModule().getProject()).getCompilerExcludes();
    FileFilter baseFilter = BuilderRegistry.getInstance().getModuleBuilderFileFilter();
    JavadocSnippetsSkipFilter snippetsSkipFilter = new JavadocSnippetsSkipFilter(getRootFile());
    return file -> baseFilter.accept(file) && !excludes.isExcluded(file) && snippetsSkipFilter.accept(file) && filterForExcludedPatterns.accept(file);
  }

  @Override
  public final boolean isGenerated() {
    return isGeneratedSources;
  }
}
