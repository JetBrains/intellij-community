/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.impl;

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class BuildTargetRegistryImpl implements BuildTargetRegistry {
  private final List<BuildTarget<?>> myAllTargets;
  private final Map<BuildTargetType<?>, List<? extends BuildTarget<?>>> myTargets;
  private final Map<JpsModule, List<ModuleBasedTarget>> myModuleBasedTargets;

  public BuildTargetRegistryImpl(JpsModel model) {
    myTargets = new THashMap<>();
    myModuleBasedTargets = new THashMap<>();
    List<List<? extends BuildTarget<?>>> targetsByType = new ArrayList<>();
    for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
      List<? extends BuildTarget<?>> targets = type.computeAllTargets(model);
      myTargets.put(type, targets);
      targetsByType.add(targets);
      for (BuildTarget<?> target : targets) {
        if (target instanceof ModuleBasedTarget) {
          final ModuleBasedTarget t = (ModuleBasedTarget)target;
          final JpsModule module = t.getModule();
          List<ModuleBasedTarget> list = myModuleBasedTargets.get(module);
          if (list == null) {
            list = new ArrayList<>();
            myModuleBasedTargets.put(module, list);
          }
          list.add(t);
        }
      }
    }
    myAllTargets = Collections.unmodifiableList(ContainerUtil.concat(targetsByType));
  }

  @NotNull
  @Override
  public Collection<ModuleBasedTarget<?>> getModuleBasedTargets(@NotNull JpsModule module, @NotNull BuildTargetRegistry.ModuleTargetSelector selector) {
    final List<ModuleBasedTarget> targets = myModuleBasedTargets.get(module);
    if (targets == null || targets.isEmpty()) {
      return Collections.emptyList();
    }
    final List<ModuleBasedTarget<?>> result = new SmartList<>();
    for (ModuleBasedTarget target : targets) {
      switch (selector) {
        case ALL:
          result.add(target);
          break;
        case PRODUCTION:
          if (!target.isTests()) {
            result.add(target);
          }
          break;
        case TEST:
          if (target.isTests()) {
            result.add(target);
          }
      }
    }
    return result;
  }

  @Override
  @NotNull
  public <T extends BuildTarget<?>> List<T> getAllTargets(@NotNull BuildTargetType<T> type) {
    //noinspection unchecked
    return (List<T>)myTargets.get(type);
  }

  @NotNull
  @Override
  public List<BuildTarget<?>> getAllTargets() {
    return myAllTargets;
  }
}
