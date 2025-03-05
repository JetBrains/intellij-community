// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
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
 * E.g., any Java module has two build targets: production output and test output. Custom build targets (not based on a module)
 * cannot have cyclic dependencies on each other.
 * <p>
 * When parallel compilation is enabled, build targets that don't have any dependencies on each other may be built at the same
 * time in different threads.
 *
 * @see BuildTargetType
 */
public abstract class BuildTarget<R extends BuildRootDescriptor> {
  private final @NotNull BuildTargetType<? extends BuildTarget<R>> myTargetType;

  protected BuildTarget(@NotNull BuildTargetType<? extends BuildTarget<R>> targetType) {
    myTargetType = targetType;
  }

  /**
   * @return id of the target which must be unique among all targets of the same type
   * @see BuildTargetLoader#createTarget(String)
   */
  public abstract @NotNull String getId();

  public final @NotNull BuildTargetType<? extends BuildTarget<R>> getTargetType() {
    return myTargetType;
  }

  /**
   * Calculates the dependencies of this build target.
   *
   * @param targetRegistry the registry of all targets existing in the project.
   * @param outputIndex    the index of output files by target.
   */
  public abstract @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, 
                                                                          @NotNull TargetOutputIndex outputIndex);

  /**
   * Allows the build target to tag the current project settings relevant to the build of this target
   * (e.g., the language level of a Java module) so that the target is fully recompiled when those settings change.
   *
   * @param projectDescriptor  the complete state of a compilation invocation
   * @param out the print writer to which the project settings can be written (the settings are compared with the ones
   *            written during the invocation of the same method in a previous compilation).
   */
  public void writeConfiguration(@NotNull ProjectDescriptor projectDescriptor, @NotNull PrintWriter out) {
  }

  /**
   * Returns the list of root directories which contain input files for this target.
   * The build process will track files under these roots and pass modified and deleted files to the builders via {@link DirtyFilesHolder}.
   * @see AdditionalRootsProviderService
   * @see org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider
   */
  public abstract @NotNull @Unmodifiable List<R> computeRootDescriptors(
    @NotNull JpsModel model,
    @NotNull ModuleExcludeIndex index,
    @NotNull IgnoredFileIndex ignoredFileIndex,
    @NotNull BuildDataPaths dataPaths
  );

  /**
   * Finds a source root by its serialized ID.
   *
   * @param rootId    the serialized root ID (produced by {@link BuildRootDescriptor#getRootId()})
   * @param rootIndex the index of build roots.
   * @return the build root or null if no root with this ID exists.
   */
  public abstract @Nullable R findRootDescriptor(@NotNull String rootId, @NotNull BuildRootIndex rootIndex);

  public abstract @NotNull String getPresentableName();

  /**
   * Returns the list of output directories in which this target is going to produce its output. (The specific
   * files produced need to be reported by {@link org.jetbrains.jps.incremental.TargetBuilder#build} through
   * <p>
   * {@link BuildOutputConsumer#registerOutputFile}.)
   * @param context the compilation context.
   * @return the collection of output roots.
   */
  public @Unmodifiable @NotNull Collection<File> getOutputRoots(@NotNull CompileContext context) {
    return List.of();
  }

  @Override
  public String toString() {
    return getPresentableName();
  }
}
