// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.dependencies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Downloads missing Maven repository libraries from all modules in the project. The corresponding {@link ProjectDependenciesResolvingTarget target}
 * isn't included into regular builds so this builder isn't used (and {@link DependencyResolvingBuilder} is used instead). However in build
 * scripts we may need to download libraries without compiling project (e.g. if compiled class-files are provided by another build) and
 * therefore may use this target to download the libraries (see org.jetbrains.intellij.build.impl.JpsCompilationRunner).
 */
public final class ProjectDependenciesResolver extends TargetBuilder<BuildRootDescriptor, ProjectDependenciesResolver.ProjectDependenciesResolvingTarget> {
  public static final String TARGET_TYPE_ID = "project-dependencies-resolving";

  public ProjectDependenciesResolver() {
    super(Collections.singletonList(ProjectDependenciesResolvingTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull ProjectDependenciesResolvingTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, ProjectDependenciesResolvingTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) {
    context.processMessage(new ProgressMessage("Resolving repository libraries in the project..."));
    try {
      DependencyResolvingBuilder.resolveMissingDependencies(context, context.getProjectDescriptor().getProject().getModules(),
                                                            BuildTargetChunk.forSingleTarget(target));
    }
    catch (Exception e) {
      DependencyResolvingBuilder.reportError(context, "project", e);
    }
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Project Dependencies Resolver";
  }

  public static class ProjectDependenciesResolvingTarget extends BuildTarget<BuildRootDescriptor> {
    public ProjectDependenciesResolvingTarget() {
      super(ProjectDependenciesResolvingTargetType.INSTANCE);
    }

    @Override
    public String getId() {
      return "project";
    }

    @Override
    public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                            ModuleExcludeIndex index,
                                                            IgnoredFileIndex ignoredFileIndex,
                                                            BuildDataPaths dataPaths) {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
      return null;
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return "Project Dependencies Resolving";
    }

    @NotNull
    @Override
    public Collection<File> getOutputRoots(CompileContext context) {
      return Collections.emptyList();
    }
  }

  public static class ProjectDependenciesResolvingTargetType extends BuildTargetType<ProjectDependenciesResolvingTarget> {
    public static final ProjectDependenciesResolvingTargetType INSTANCE = new ProjectDependenciesResolvingTargetType();

    public ProjectDependenciesResolvingTargetType() {
      super(TARGET_TYPE_ID);
    }

    @NotNull
    @Override
    public List<ProjectDependenciesResolvingTarget> computeAllTargets(@NotNull JpsModel model) {
      return Collections.singletonList(new ProjectDependenciesResolvingTarget());
    }

    @NotNull
    @Override
    public BuildTargetLoader<ProjectDependenciesResolvingTarget> createLoader(@NotNull JpsModel model) {
      return new BuildTargetLoader<ProjectDependenciesResolvingTarget>() {
        @Override
        public ProjectDependenciesResolvingTarget createTarget(@NotNull String targetId) {
          return new ProjectDependenciesResolvingTarget();
        }
      };
    }
  }
}
