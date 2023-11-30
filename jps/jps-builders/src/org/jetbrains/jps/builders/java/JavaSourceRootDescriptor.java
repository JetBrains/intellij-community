// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

public class JavaSourceRootDescriptor extends BuildRootDescriptor {
  private final FileFilter myFilterForExcludedPatterns;
  public final @NotNull File root;
  public final @NotNull ModuleBuildTarget target;
  public final boolean isGeneratedSources;
  public final boolean isTemp;
  private final String myPackagePrefix;
  private final Set<File> myExcludes;

  /**
   * @deprecated use {@link #JavaSourceRootDescriptor(File, ModuleBuildTarget, boolean, boolean, String, Set, FileFilter)} instead;
   * this constructor method doesn't honor excluded patterns which may be specified for the module.
   */
  @Deprecated
  public JavaSourceRootDescriptor(@NotNull File root,
                                  @NotNull ModuleBuildTarget target,
                                  boolean isGenerated,
                                  boolean isTemp,
                                  @NotNull String packagePrefix,
                                  @NotNull Set<File> excludes) {
    this(root, target, isGenerated, isTemp, packagePrefix, excludes, FileFilters.EVERYTHING);
  }

  public JavaSourceRootDescriptor(@NotNull File root,
                                  @NotNull ModuleBuildTarget target,
                                  boolean isGenerated,
                                  boolean isTemp,
                                  @NotNull String packagePrefix,
                                  @NotNull Set<File> excludes,
                                  @NotNull FileFilter filterForExcludedPatterns) {
    this.root = root;
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
           ", root=" + root +
           ", generated=" + isGeneratedSources +
           '}';
  }

  @Override
  public @NotNull Set<File> getExcludedRoots() {
    return myExcludes;
  }

  public @NotNull String getPackagePrefix() {
    return myPackagePrefix;
  }

  @Override
  public @NotNull String getRootId() {
    return FileUtil.toSystemIndependentName(root.getPath());
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
