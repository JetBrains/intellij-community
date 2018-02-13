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

import org.jetbrains.jps.builders.BuildTarget;

import java.util.Set;

/**
 * @author nik
 */
public class BuildTargetChunk {
  private final Set<BuildTarget<?>> myTargets;

  public BuildTargetChunk(Set<BuildTarget<?>> targets) {
    myTargets = targets;
  }

  public Set<BuildTarget<?>> getTargets() {
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
}
