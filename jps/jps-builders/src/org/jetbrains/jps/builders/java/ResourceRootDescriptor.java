/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
  @NotNull private final File myRoot;
  @NotNull private final ResourcesTarget myTarget;
  @NotNull private final String myPackagePrefix;
  @NotNull private final Set<File> myExcludes;
  protected final FileFilter myFilterForExcludedPatterns;

  /**
   * @deprecated use {@link #ResourceRootDescriptor(File, ResourcesTarget, String, Set, FileFilter)} instead; this method doesn't honor
   * excluded patterns which may be specified for the module.
   */
  @Deprecated
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
  public File getRootFile() {
    return myRoot;
  }

  @NotNull
  @Override
  public Set<File> getExcludedRoots() {
    return myExcludes;
  }

  @NotNull
  @Override
  public ResourcesTarget getTarget() {
    return myTarget;
  }

  @NotNull
  public String getPackagePrefix() {
    return myPackagePrefix;
  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
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
  public String getRootId() {
    return FileUtil.toSystemIndependentName(myRoot.getPath());
  }
}
