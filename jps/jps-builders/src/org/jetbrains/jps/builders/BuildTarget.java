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
 * A single unit of the compilation process of a specific project. Has a number of inputs (individual files or directories with
 * filter support). Places its output in a specific set of output roots. Can have dependencies on other build targets.
 * E.g. any Java module has two build targets: production output and test output. Custom build targets (not based on a module)
 * cannot have cyclic dependencies on each other.
 * <p>
 * When parallel compilation is enabled, build targets that don't have any dependencies on each other may be built at the same
 * time in different threads.
 *
 * @see BuildTargetType
 * @author nik
 */
public abstract class BuildTarget<R extends BuildRootDescriptor> {
  private final BuildTargetType<?> myTargetType;

  protected BuildTarget(BuildTargetType<?> targetType) {
    myTargetType = targetType;
  }

  /**
   * @return id of the target which must be unique among all targets of the same type
   * @see BuildTargetLoader#createTarget(String)
   */
  public abstract String getId();

  public final BuildTargetType<?> getTargetType() {
    return myTargetType;
  }

  /**
   * Calculates the dependencies of this build target.
   *
   * @param targetRegistry the registry of all targets existing in the project.
   * @param outputIndex    the index of output files by target.
   * @return
   */
  public abstract Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex);

  /**
   * Allows the build target to tag the current project settings relevant to the build of this target
   * (e.g the language level of a Java module) so that the target is fully recompiled when those settings
   * change.
   *
   * @param pd  the complete state of a compilation invocation
   * @param out the print writer to which the project settings can be written (the settings are compared with the ones
   *            written during the invocation of the same method in a previous compilation).
   */
  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
  }

  /**
   * Returns the list of root directories which contain input files for this target. The build process will track files under these root
   * and pass modified and deleted files to the builders via {@link DirtyFilesHolder}.
   * @see AdditionalRootsProviderService
   * @see org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider
   */
  @NotNull
  public abstract List<R> computeRootDescriptors(JpsModel model,
                                                 ModuleExcludeIndex index,
                                                 IgnoredFileIndex ignoredFileIndex,
                                                 BuildDataPaths dataPaths);

  /**
   * Finds a source root by its serialized ID.
   *
   * @param rootId    the serialized root ID (produced by {@link BuildRootDescriptor#getRootId()})
   * @param rootIndex the index of build roots.
   * @return the build root or null if no root with this ID exists.
   */
  @Nullable
  public abstract R findRootDescriptor(String rootId, BuildRootIndex rootIndex);

  @NotNull
  public abstract String getPresentableName();

  /**
   * Returns the list of output directories in which this target is going to produce its output. (The specific
   * files produced need to be reported by {@link org.jetbrains.jps.incremental.TargetBuilder#build} through
   *
   * {@link org.jetbrains.jps.builders.BuildOutputConsumer#registerOutputFile}.)
   * @param context the compilation context.
   * @return the collection of output roots.
   */
  @NotNull
  public abstract Collection<File> getOutputRoots(CompileContext context);

  @Override
  public String toString() {
    return getPresentableName();
  }
}
