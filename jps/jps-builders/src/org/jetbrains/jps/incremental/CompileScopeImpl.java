// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

@ApiStatus.Internal
public final class CompileScopeImpl extends CompileScope {
  private final Collection<? extends BuildTargetType<?>> myTypes;
  private final Collection<BuildTargetType<?>> myTypesToForceBuild;
  private final Collection<BuildTarget<?>> myTargets;
  private final Map<BuildTarget<?>, Set<Path>> targetToFiles;
  private final Map<BuildTarget<?>, Set<Path>> targetToIndirectlyAffectedFiles = Collections.synchronizedMap(new HashMap<>());

  @SuppressWarnings("IO_FILE_USAGE")
  public CompileScopeImpl(@NotNull @Unmodifiable Collection<? extends BuildTargetType<?>> types,
                          @NotNull @Unmodifiable Collection<? extends BuildTargetType<?>> typesToForceBuild,
                          @NotNull @Unmodifiable Collection<BuildTarget<?>> targets,
                          @NotNull @Unmodifiable Map<BuildTarget<?>, Set<File>> files) {
    myTypes = types;
    myTypesToForceBuild = new HashSet<>();
    boolean forceBuildAllModuleBasedTargets = false;
    for (BuildTargetType<?> type : typesToForceBuild) {
      myTypesToForceBuild.add(type);
      forceBuildAllModuleBasedTargets |= type instanceof JavaModuleBuildTargetType;
    }
    if (forceBuildAllModuleBasedTargets) {
      for (BuildTargetType<?> targetType : TargetTypeRegistry.getInstance().getTargetTypes()) {
        if (targetType instanceof ModuleBasedBuildTargetType<?>) {
          myTypesToForceBuild.add(targetType);
        }
      }
    }
    myTargets = targets;

    if (files.isEmpty()) {
      this.targetToFiles = Map.of();
    }
    else {
      Map<BuildTarget<?>, Set<Path>> map = new HashMap<>(files.size());
      for (Map.Entry<BuildTarget<?>, Set<File>> entry : files.entrySet()) {
        BuildTarget<?> target = entry.getKey();
        Set<File> fileSet = entry.getValue();
        Set<Path> paths = new HashSet<>(fileSet.size());
        for (File file : fileSet) {
          paths.add(file.toPath());
        }
        map.put(target, paths);
      }
      this.targetToFiles = map;
    }
  }

  @Override
  public boolean isAffected(@NotNull BuildTarget<?> target) {
    return isWholeTargetAffected(target) || targetToFiles.containsKey(target) || targetToIndirectlyAffectedFiles.containsKey(target);
  }

  @Override
  public boolean isWholeTargetAffected(@NotNull BuildTarget<?> target) {
    return (myTypes.contains(target.getTargetType()) || myTargets.contains(target) || isAffectedByAssociatedModule(target)) && !targetToFiles.containsKey(target);
  }

  @Override
  public boolean isAllTargetsOfTypeAffected(@NotNull BuildTargetType<?> type) {
    return myTypes.contains(type) && targetToFiles.isEmpty();
  }

  @Override
  public boolean isBuildForced(@NotNull BuildTarget<?> target) {
    return targetToFiles.isEmpty() && myTypesToForceBuild.contains(target.getTargetType()) && isWholeTargetAffected(target);
  }

  @Override
  public boolean isBuildForcedForAllTargets(@NotNull BuildTargetType<?> targetType) {
    return myTypesToForceBuild.contains(targetType) && isAllTargetsOfTypeAffected(targetType);
  }

  @Override
  public boolean isBuildIncrementally(@NotNull BuildTargetType<?> targetType) {
    return !myTypesToForceBuild.contains(targetType);
  }

  @Override
  public boolean isAffected(BuildTarget<?> target, @NotNull Path file) {
    Set<Path> files = targetToFiles.get(target);
    if (files == null) {
      return isWholeTargetAffected(target) || isIndirectlyAffected(target, file);
    }
    else {
      return files.contains(file) || isIndirectlyAffected(target, file);
    }
  }

  private boolean isIndirectlyAffected(BuildTarget<?> target, @NotNull Path file) {
    synchronized (targetToIndirectlyAffectedFiles) {
      Set<Path> indirect = targetToIndirectlyAffectedFiles.get(target);
      return indirect != null && indirect.contains(file);
    }
  }

  @Override
  public void markIndirectlyAffected(BuildTarget<?> target, @NotNull Path file) {
    synchronized (targetToIndirectlyAffectedFiles) {
      targetToIndirectlyAffectedFiles.computeIfAbsent(target, k -> {
        return new HashSet<>();
      }).add(file);
    }
  }

  private boolean isAffectedByAssociatedModule(BuildTarget<?> target) {
    if (target instanceof ModuleBasedTarget) {
      final JpsModule module = ((ModuleBasedTarget<?>)target).getModule();
      // this target is associated with module
      JavaModuleBuildTargetType targetType = JavaModuleBuildTargetType.getInstance(((ModuleBasedTarget<?>)target).isTests());
      if (myTypes.contains(targetType) || myTargets.contains(new ModuleBuildTarget(module, targetType))) {
        return true;
      }
    }
    return false;
  }
}
