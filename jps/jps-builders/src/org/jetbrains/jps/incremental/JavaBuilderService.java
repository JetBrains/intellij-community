// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder;
import org.jetbrains.jps.incremental.dependencies.ProjectDependenciesResolver;
import org.jetbrains.jps.incremental.instrumentation.NotNullInstrumentingBuilder;
import org.jetbrains.jps.incremental.instrumentation.RmiStubsGenerator;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class JavaBuilderService extends BuilderService {
  @Override
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    List<BuildTargetType<?>> types = new ArrayList<>(5);
    types.addAll(JavaModuleBuildTargetType.ALL_TYPES);
    types.addAll(ResourcesTargetType.ALL_TYPES);
    types.add(ProjectDependenciesResolver.ProjectDependenciesResolvingTargetType.INSTANCE);
    return types;
  }

  @Override
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return List.of(
      new JavaBuilder(),
      new NotNullInstrumentingBuilder(),
      new RmiStubsGenerator(),
      new DependencyResolvingBuilder(),
      new JavaBackwardReferenceIndexBuilder()
    );
  }

  @Override
  public @NotNull List<? extends TargetBuilder<?, ?>> createBuilders() {
    return List.of(new ResourcesBuilder(), new ProjectDependenciesResolver());
  }
}
