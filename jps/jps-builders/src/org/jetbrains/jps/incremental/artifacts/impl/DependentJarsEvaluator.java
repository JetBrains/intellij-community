// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.jps.incremental.artifacts.instructions.DestinationInfo;
import org.jetbrains.jps.incremental.artifacts.instructions.JarDestinationInfo;
import org.jetbrains.jps.incremental.artifacts.instructions.JarInfo;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DependentJarsEvaluator {
  private final Set<JarInfo> myJars = new LinkedHashSet<>();

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
