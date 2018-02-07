/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
* @author Eugene Zhuravlev
*/
public class JavaSourceRootDescriptor extends BuildRootDescriptor {
  @NotNull
  public final File root;
  @NotNull
  public final ModuleBuildTarget target;
  public final boolean isGeneratedSources;
  public final boolean isTemp;
  private final String myPackagePrefix;
  private final Set<File> myExcludes;

  public JavaSourceRootDescriptor(@NotNull File root,
                                  @NotNull ModuleBuildTarget target,
                                  boolean isGenerated,
                                  boolean isTemp,
                                  @NotNull String packagePrefix,
                                  @NotNull Set<File> excludes) {
    this.root = root;
    this.target = target;
    this.isGeneratedSources = isGenerated;
    this.isTemp = isTemp;
    myPackagePrefix = packagePrefix;
    myExcludes = excludes;
  }

  @Override
  public String toString() {
    return "RootDescriptor{" +
           "target='" + target + '\'' +
           ", root=" + root +
           ", generated=" + isGeneratedSources +
           '}';
  }

  @NotNull
  @Override
  public Set<File> getExcludedRoots() {
    return myExcludes;
  }

  @NotNull
  public String getPackagePrefix() {
    return myPackagePrefix;
  }

  @Override
  public String getRootId() {
    return FileUtil.toSystemIndependentName(root.getPath());
  }

  @Override
  public File getRootFile() {
    return root;
  }

  @Override
  public ModuleBuildTarget getTarget() {
    return target;
  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(target.getModule().getProject()).getCompilerExcludes();
    final FileFilter baseFilter = BuilderRegistry.getInstance().getModuleBuilderFileFilter();
    return file -> baseFilter.accept(file) && !excludes.isExcluded(file);
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
