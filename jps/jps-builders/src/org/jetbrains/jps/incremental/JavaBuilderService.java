/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.BackwardReferenceIndexBuilder;
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

/**
 * @author nik
 */
public class JavaBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    List<BuildTargetType<?>> types = new ArrayList<>();
    types.addAll(JavaModuleBuildTargetType.ALL_TYPES);
    types.addAll(ResourcesTargetType.ALL_TYPES);
    types.add(ProjectDependenciesResolver.ProjectDependenciesResolvingTargetType.INSTANCE);
    return types;
  }

  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(
      new JavaBuilder(SharedThreadPool.getInstance()),
      new NotNullInstrumentingBuilder(),
      new RmiStubsGenerator(),
      new DependencyResolvingBuilder(),
      new BackwardReferenceIndexBuilder()
    );
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Arrays.asList(new ResourcesBuilder(), new ProjectDependenciesResolver());
  }
}