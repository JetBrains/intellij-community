// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@ApiStatus.Internal
public final class ClassFileDependenciesAnalyzer {
  private final DependencyContext myContext;

  public ClassFileDependenciesAnalyzer(File dependenciesDataDir, PathRelativizerService relativizer) throws IOException {
    myContext = new DependencyContext(dependenciesDataDir, relativizer);
  }

  public @NotNull Set<String> collectDependencies(String className, ClassReader classReader) {
    ClassFileRepr classFileRepr = new ClassfileAnalyzer(myContext).analyze(myContext.get(className), classReader, false);
    if (classFileRepr == null) return Collections.emptySet();
    final int classNameId = classFileRepr.name;
    Set<String> classDependencies = new LinkedHashSet<>();
    for (UsageRepr.Usage usage : classFileRepr.getUsages()) {
      int owner = usage.getOwner();
      if (owner != classNameId) {
        classDependencies.add(myContext.getValue(owner));
      }
    }
    return classDependencies;
  }

  public void close() {
    myContext.close();
  }
}
