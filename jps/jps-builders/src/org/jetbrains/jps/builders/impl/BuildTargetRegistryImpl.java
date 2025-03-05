// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

@ApiStatus.Internal
public final class BuildTargetRegistryImpl implements BuildTargetRegistry {
  private final @Unmodifiable List<BuildTarget<?>> allTargets;
  private final @Unmodifiable Map<BuildTargetType<?>, List<? extends BuildTarget<?>>> targets;
  private final @Unmodifiable Map<JpsModule, List<ModuleBasedTarget<?>>> moduleBasedTargets;

  public BuildTargetRegistryImpl(JpsModel model) {
    Map<BuildTargetType<?>, List<? extends BuildTarget<?>>> targets = new HashMap<>();
    Map<JpsModule, List<ModuleBasedTarget<?>>> moduleBasedTargets = new HashMap<>();
    List<BuildTarget<?>> allTargets = new ArrayList<>();
    for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
      List<? extends BuildTarget<?>> targetsPerType = type.computeAllTargets(model);
      targets.put(type, targetsPerType);
      allTargets.addAll(targetsPerType);
      for (BuildTarget<?> target : targetsPerType) {
        if (target instanceof ModuleBasedTarget) {
          ModuleBasedTarget<?> t = (ModuleBasedTarget<?>)target;
          JpsModule module = t.getModule();
          List<ModuleBasedTarget<?>> list = moduleBasedTargets.get(module);
          if (list == null) {
            list = new ArrayList<>();
            moduleBasedTargets.put(module, list);
          }
          list.add(t);
        }
      }
    }
    this.targets = Map.copyOf(targets);
    this.moduleBasedTargets = Map.copyOf(moduleBasedTargets);
    this.allTargets = List.copyOf(allTargets);
  }

  @Override
  public @NotNull Collection<ModuleBasedTarget<?>> getModuleBasedTargets(@NotNull JpsModule module, @NotNull BuildTargetRegistry.ModuleTargetSelector selector) {
    final List<ModuleBasedTarget<?>> targets = moduleBasedTargets.get(module);
    if (targets == null || targets.isEmpty()) {
      return List.of();
    }

    List<ModuleBasedTarget<?>> result = new ArrayList<>();
    for (ModuleBasedTarget<?> target : targets) {
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
  public @NotNull <T extends BuildTarget<?>> List<T> getAllTargets(@NotNull BuildTargetType<T> type) {
    //noinspection unchecked
    return (List<T>)targets.getOrDefault(type, List.of());
  }

  @Override
  public @NotNull List<BuildTarget<?>> getAllTargets() {
    return allTargets;
  }
}
