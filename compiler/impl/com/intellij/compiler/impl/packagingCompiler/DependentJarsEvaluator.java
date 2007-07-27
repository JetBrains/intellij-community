/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.util.Pair;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author nik
 */
public class DependentJarsEvaluator {
  private Set<JarInfo> myJars = new LinkedHashSet<JarInfo>();

  public void addJarWithDependencies(final JarInfo jarInfo) {
    if (myJars.add(jarInfo)) {
      for (JarDestinationInfo destination : jarInfo.getJarDestinations()) {
        addJarWithDependencies(destination.getJarInfo());
      }
      for (Pair<String, JarInfo> pair : jarInfo.getPackedJars()) {
        addJarWithDependencies(pair.getSecond());
      }
    }
  }

  public Set<JarInfo> getJars() {
    return myJars;
  }
}
