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

/**
 * Base class for all {@link BuildTarget} implementations which are bound to sources of a module and need to be built each time 'Build Module'
 * action is invoked in IDE. If your target should be built only by explicit action extend {@link BuildTarget} directly instead.
 *
 * @author Eugene Zhuravlev
 */
public abstract class ModuleBasedTarget<R extends BuildRootDescriptor> extends BuildTarget<R> {
  protected final JpsModule myModule;

  public ModuleBasedTarget(ModuleBasedBuildTargetType<?> targetType, @NotNull JpsModule module) {
    super(targetType);
    myModule = module;
  }

  @NotNull
  public JpsModule getModule() {
    return myModule;
  }

  /**
   * @return {@code true} if the target should be built before {@link org.jetbrains.jps.incremental.ModuleBuildTarget} of the corresponding module.
   */
  public boolean isCompiledBeforeModuleLevelBuilders() {
    return false;
  }

  public abstract boolean isTests();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ModuleBasedTarget)) {
      return false;
    }

    ModuleBasedTarget target = (ModuleBasedTarget)o;
    return getTargetType() == target.getTargetType() && getId().equals(target.getId());
  }

  @Override
  public int hashCode() {
    return 31 * getId().hashCode() + getTargetType().hashCode();
  }

}
