// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;

import java.util.Collections;
import java.util.Set;

/**
 * Represents a single build target or a set of build targets which circularly depend on each other.
 */
public final class BuildTargetChunk {
  private final Set<? extends BuildTarget<?>> myTargets;

  public BuildTargetChunk(@NotNull Set<? extends BuildTarget<?>> targets) {
    myTargets = targets;
  }

  public @NotNull Set<? extends BuildTarget<?>> getTargets() {
    return myTargets;
  }

  @Override
  public String toString() {
    return myTargets.toString();
  }
  
  public String getPresentableName() {
    final String name = myTargets.iterator().next().getPresentableName();
    final int size = myTargets.size();
    return size > 1 ? name + " and " + (size - 1) + " more" : name;
  }

  public static @NotNull BuildTargetChunk forSingleTarget(@NotNull BuildTarget<?> target) {
    return new BuildTargetChunk(Collections.singleton(target));
  }

  public static @NotNull BuildTargetChunk forModulesChunk(@NotNull ModuleChunk chunk) {
    return new BuildTargetChunk(chunk.getTargets());
  }
}
