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
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class BuildTarget<R extends BuildRootDescriptor> {
  private final BuildTargetType<?> myTargetType;

  protected BuildTarget(BuildTargetType<?> targetType) {
    myTargetType = targetType;
  }

  public abstract String getId();

  public final BuildTargetType<?> getTargetType() {
    return myTargetType;
  }

  public abstract Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex);

  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
  }

  @NotNull
  public abstract List<R> computeRootDescriptors(JpsModel model,
                                                 ModuleExcludeIndex index,
                                                 IgnoredFileIndex ignoredFileIndex,
                                                 BuildDataPaths dataPaths);

  @Nullable
  public abstract R findRootDescriptor(String rootId, BuildRootIndex rootIndex);

  @NotNull
  public abstract String getPresentableName();

  @NotNull
  public abstract Collection<File> getOutputRoots(CompileContext context);

  @Override
  public String toString() {
    return getPresentableName();
  }
}
