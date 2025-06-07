// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.resolving.repository.libraries.in.the.project")));
    try {
      DependencyResolvingBuilder.resolveAllMissingDependenciesInProject(context, BuildTargetChunk.forSingleTarget(target));
    }
    catch (Exception e) {
      DependencyResolvingBuilder.reportError(context, "project", e);
    }
  }

  @Override
  public @NotNull String getPresentableName() {
    return JpsBuildBundle.message("builder.name.project.dependencies.resolver");
  }

  public static final class ProjectDependenciesResolvingTarget extends BuildTarget<BuildRootDescriptor> {
    public ProjectDependenciesResolvingTarget() {
      super(ProjectDependenciesResolvingTargetType.INSTANCE);
    }

    @Override
    public @NotNull String getId() {
      return "project";
    }

    @Override
    public @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, @NotNull TargetOutputIndex outputIndex) {
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<BuildRootDescriptor> computeRootDescriptors(@NotNull JpsModel model,
                                                                     @NotNull ModuleExcludeIndex index,
                                                                     @NotNull IgnoredFileIndex ignoredFileIndex,
                                                                     @NotNull BuildDataPaths dataPaths) {
      return Collections.emptyList();
    }

    @Override
    public @Nullable BuildRootDescriptor findRootDescriptor(@NotNull String rootId, @NotNull BuildRootIndex rootIndex) {
      return null;
    }

    @Override
    public @NotNull String getPresentableName() {
      return "Project Dependencies Resolving";
    }
  }

  public static final class ProjectDependenciesResolvingTargetType extends BuildTargetType<ProjectDependenciesResolvingTarget> {
    public static final ProjectDependenciesResolvingTargetType INSTANCE = new ProjectDependenciesResolvingTargetType();

    public ProjectDependenciesResolvingTargetType() {
      super(TARGET_TYPE_ID);
    }

    @Override
    public @NotNull List<ProjectDependenciesResolvingTarget> computeAllTargets(@NotNull JpsModel model) {
      return List.of(new ProjectDependenciesResolvingTarget());
    }

    @Override
    public @NotNull BuildTargetLoader<ProjectDependenciesResolvingTarget> createLoader(@NotNull JpsModel model) {
      return new BuildTargetLoader<>() {
        @Override
        public ProjectDependenciesResolvingTarget createTarget(@NotNull String targetId) {
          return new ProjectDependenciesResolvingTarget();
        }
      };
    }
  }
}
