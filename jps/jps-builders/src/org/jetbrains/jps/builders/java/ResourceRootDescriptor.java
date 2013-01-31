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
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*         Date: 1/3/12
*/
public final class ResourceRootDescriptor extends BuildRootDescriptor {

  @NotNull private final File myRoot;
  @NotNull private final ResourcesTarget myTarget;
  private final boolean myGenerated;
  @NotNull private final String myPackagePrefix;
  @NotNull private final Set<File> myExcludes;

  public ResourceRootDescriptor(@NotNull File root, @NotNull ResourcesTarget target, boolean isGenerated, @NotNull String packagePrefix, @NotNull Set<File> excludes) {
    myRoot = root;
    myTarget = target;
    myGenerated = isGenerated;
    myPackagePrefix = packagePrefix;
    myExcludes = excludes;
  }

  @Override
  public String getRootId() {
    return FileUtil.toSystemIndependentName(myRoot.getPath());
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
    final JpsProject project = myTarget.getModule().getProject();
    final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    final JpsCompilerExcludes excludes = configuration.getCompilerExcludes();
    return new FileFilter() {
      @Override
      public boolean accept(File file) {
        return !excludes.isExcluded(file) && configuration.isResourceFile(file, getRootFile());
      }
    };
  }

  @Override
  public boolean isGenerated() {
    return myGenerated;
  }

  @Override
  public String toString() {
    return "ResourceRootDescriptor{" +
           "target='" + myTarget + '\'' +
           ", root=" + myRoot +
           ", generated=" + myGenerated +
           '}';
  }

  @Override
  public boolean canUseFileCache() {
    return true;
  }
}
