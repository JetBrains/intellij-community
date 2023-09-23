// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

public final class CompileScopeImpl extends CompileScope {
  private final Collection<? extends BuildTargetType<?>> myTypes;
  private final Collection<BuildTargetType<?>> myTypesToForceBuild;
  private final Collection<BuildTarget<?>> myTargets;
  private final Map<BuildTarget<?>, Set<File>> myFiles;
  private final Map<BuildTarget<?>, Set<File>> myIndirectlyAffectedFiles = Collections.synchronizedMap(new HashMap<>());

  public CompileScopeImpl(@NotNull Collection<? extends BuildTargetType<?>> types,
                          @NotNull Collection<? extends BuildTargetType<?>> typesToForceBuild,
                          @NotNull Collection<BuildTarget<?>> targets,
                          @NotNull Map<BuildTarget<?>, Set<File>> files) {
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
    myFiles = files;
  }

  @Override
  public boolean isAffected(@NotNull BuildTarget<?> target) {
    return isWholeTargetAffected(target) || myFiles.containsKey(target) || myIndirectlyAffectedFiles.containsKey(target);
  }

  @Override
  public boolean isWholeTargetAffected(@NotNull BuildTarget<?> target) {
    return (myTypes.contains(target.getTargetType()) || myTargets.contains(target) || isAffectedByAssociatedModule(target)) && !myFiles.containsKey(target);
  }

  @Override
  public boolean isAllTargetsOfTypeAffected(@NotNull BuildTargetType<?> type) {
    return myTypes.contains(type) && myFiles.isEmpty();
  }

  @Override
  public boolean isBuildForced(@NotNull BuildTarget<?> target) {
    return myFiles.isEmpty() && myTypesToForceBuild.contains(target.getTargetType()) && isWholeTargetAffected(target);
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
  public boolean isAffected(BuildTarget<?> target, @NotNull File file) {
    final Set<File> files = myFiles.isEmpty()? null : myFiles.get(target);
    if (files == null) {
      return isWholeTargetAffected(target) || isIndirectlyAffected(target, file);
    }
    return files.contains(file) || isIndirectlyAffected(target, file);
  }

  private boolean isIndirectlyAffected(BuildTarget<?> target, @NotNull File file) {
    synchronized (myIndirectlyAffectedFiles) {
      final Set<File> indirect = myIndirectlyAffectedFiles.get(target);
      return indirect != null && indirect.contains(file);
    }
  }

  @Override
  public void markIndirectlyAffected(BuildTarget<?> target, @NotNull File file) {
    synchronized (myIndirectlyAffectedFiles) {
      Set<File> files = myIndirectlyAffectedFiles.get(target);
      if (files == null) {
        files = new HashSet<>();
        myIndirectlyAffectedFiles.put(target, files);
      }
      files.add(file);
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
