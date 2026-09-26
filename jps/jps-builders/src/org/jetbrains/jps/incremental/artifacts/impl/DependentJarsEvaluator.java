// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.artifacts.instructions.DestinationInfo;
import org.jetbrains.jps.incremental.artifacts.instructions.JarDestinationInfo;
import org.jetbrains.jps.incremental.artifacts.instructions.JarInfo;

import java.util.LinkedHashSet;
import java.util.Set;

@ApiStatus.Internal
public final class DependentJarsEvaluator {
  private final Set<JarInfo> jars = new LinkedHashSet<>();

  public void addJarWithDependencies(@NotNull JarInfo jarInfo) {
    if (!jars.add(jarInfo)) {
      return;
    }

    DestinationInfo destination = jarInfo.getDestination();
    if (destination instanceof JarDestinationInfo) {
      addJarWithDependencies(((JarDestinationInfo)destination).getJarInfo());
    }
    for (Pair<String, Object> pair : jarInfo.getContent()) {
      if (pair.getSecond() instanceof JarInfo) {
        addJarWithDependencies((JarInfo)pair.getSecond());
      }
    }
  }

  public Set<JarInfo> getJars() {
    return jars;
  }
}
