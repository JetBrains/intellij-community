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

public class CompileScopeImpl extends CompileScope {
  private final Collection<? extends BuildTargetType<?>> myTypes;
  private final Collection<BuildTargetType<?>> myTypesToForceBuild;
  private final Collection<BuildTarget<?>> myTargets;
  private final Map<BuildTarget<?>, Set<File>> myFiles;
  private final Map<BuildTarget<?>, Set<File>> myIndirectlyAffectedFiles = Collections.synchronizedMap(new HashMap<>());

  public CompileScopeImpl(Collection<? extends BuildTargetType<?>> types,
                          Collection<? extends BuildTargetType<?>> typesToForceBuild,
                          Collection<BuildTarget<?>> targets,
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
