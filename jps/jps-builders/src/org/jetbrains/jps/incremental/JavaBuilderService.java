// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

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
import org.jetbrains.jps.service.SharedThreadPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class JavaBuilderService extends BuilderService {
  @Override
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    List<BuildTargetType<?>> types = new ArrayList<>();
    types.addAll(JavaModuleBuildTargetType.ALL_TYPES);
    types.addAll(ResourcesTargetType.ALL_TYPES);
    types.add(ProjectDependenciesResolver.ProjectDependenciesResolvingTargetType.INSTANCE);
    return types;
  }

  @Override
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(
      new JavaBuilder(SharedThreadPool.getInstance()),
      new NotNullInstrumentingBuilder(),
      new RmiStubsGenerator(),
      new DependencyResolvingBuilder(),
      new JavaBackwardReferenceIndexBuilder()
    );
  }

  @Override
  public @NotNull List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Arrays.asList(new ResourcesBuilder(), new ProjectDependenciesResolver());
  }
}
