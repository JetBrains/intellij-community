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
package org.jetbrains.jps.builders.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;

import java.util.Collections;
import java.util.Set;

/**
 * Represents a single build target or a set of build targets which circularly depend on each other.
 *
 * @author nik
 */
public class BuildTargetChunk {
  private final Set<? extends BuildTarget<?>> myTargets;

  public BuildTargetChunk(Set<? extends BuildTarget<?>> targets) {
    myTargets = targets;
  }

  public Set<? extends BuildTarget<?>> getTargets() {
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

  public static BuildTargetChunk forSingleTarget(@NotNull BuildTarget<?> target) {
    return new BuildTargetChunk(Collections.singleton(target));
  }

  public static BuildTargetChunk forModulesChunk(@NotNull ModuleChunk chunk) {
    return new BuildTargetChunk(chunk.getTargets());
  }
}
