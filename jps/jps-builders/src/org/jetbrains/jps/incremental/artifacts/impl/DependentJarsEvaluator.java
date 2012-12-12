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

package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.jps.incremental.artifacts.instructions.DestinationInfo;
import org.jetbrains.jps.incremental.artifacts.instructions.JarDestinationInfo;
import org.jetbrains.jps.incremental.artifacts.instructions.JarInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author nik
 */
public class DependentJarsEvaluator {
  private final Set<JarInfo> myJars = new LinkedHashSet<JarInfo>();

  public void addJarWithDependencies(final JarInfo jarInfo) {
    if (myJars.add(jarInfo)) {
      final DestinationInfo destination = jarInfo.getDestination();
      if (destination instanceof JarDestinationInfo) {
        addJarWithDependencies(((JarDestinationInfo)destination).getJarInfo());
      }
      for (Pair<String, Object> pair : jarInfo.getContent()) {
        if (pair.getSecond() instanceof JarInfo) {
          addJarWithDependencies((JarInfo)pair.getSecond());
        }
      }
    }
  }

  public Set<JarInfo> getJars() {
    return myJars;
  }
}
