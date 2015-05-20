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
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;
import java.util.List;

/**
 * Allows to enumerate all build targets existing in a project.
 *
 * @author Eugene Zhuravlev
 * @since 10/27/12
 */
public interface BuildTargetRegistry {
  /**
   * Returns all build targets of a specified type.
   */
  @NotNull
  <T extends BuildTarget<?>>
  List<T> getAllTargets(@NotNull BuildTargetType<T> type);

  /**
   * Returns all build targets existing in the project.
   */
  @NotNull
  List<BuildTarget<?>> getAllTargets();

  enum ModuleTargetSelector {
    PRODUCTION, TEST, ALL
  }

  /**
   * Returns the module-based targets of the specified module that have the specified type.
   */
  @NotNull
  Collection<ModuleBasedTarget<?>> getModuleBasedTargets(@NotNull JpsModule module, @NotNull ModuleTargetSelector selector);
}
