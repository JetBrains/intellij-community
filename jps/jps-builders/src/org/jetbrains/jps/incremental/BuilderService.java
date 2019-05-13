/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.jps.builders.BuildTargetType;

import java.util.Collections;
import java.util.List;

/**
 * The main entry point for the external build system plugins. Implementations of this class are registered as Java services, by
 * creating a file META-INF/services/org.jetbrains.jps.incremental.BuilderService containing the qualified name of your implementation
 * class.
 *
 * @author nik
 */
public abstract class BuilderService {
  /**
   * Returns the list of build target types contributed by this plugin. If it only participates in the compilation
   * of regular Java modules, you don't need to return anything here.
   */
  @NotNull
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Collections.emptyList();
  }

  /**
   * Returns the list of Java module builder extensions contributed by this plugin.
   */
  @NotNull
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Collections.emptyList();
  }

  /**
   * Returns the list of non-module target builders contributed by this plugin.
   */
  @NotNull
  public List<? extends TargetBuilder<?,?>> createBuilders() {
    return Collections.emptyList();
  }
}
