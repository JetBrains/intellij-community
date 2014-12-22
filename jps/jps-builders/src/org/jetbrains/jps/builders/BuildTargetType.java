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
import org.jetbrains.jps.model.JpsModel;

import java.util.List;

/**
 * The type of a build target. For example, there is a build target type for Java production and another for Java tests.
 *
 * @author nik
 * @see org.jetbrains.jps.incremental.BuilderService#getTargetTypes()
 */
public abstract class BuildTargetType<T extends BuildTarget<?>> {
  private final String myTypeId;

  protected BuildTargetType(String typeId) {
    myTypeId = typeId;
  }

  public final String getTypeId() {
    return myTypeId;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof BuildTargetType && ((BuildTargetType)obj).myTypeId.equals(myTypeId);
  }

  @Override
  public int hashCode() {
    return myTypeId.hashCode();
  }

  /**
   * Finds all targets of the given type that exist in the given project.
   * @param model the model instance representing a project.
   * @return the list of targets.
   */
  @NotNull
  public abstract List<T> computeAllTargets(@NotNull JpsModel model);

  @NotNull
  public abstract BuildTargetLoader<T> createLoader(@NotNull JpsModel model);
}
