// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ClassFileDependenciesAnalyzer {
  private final DependencyContext myContext;

  public ClassFileDependenciesAnalyzer(File dependenciesDataDir, PathRelativizerService relativizer) throws IOException {
    myContext = new DependencyContext(dependenciesDataDir, relativizer);
  }

  @NotNull
  public Set<String> collectDependencies(String className, ClassReader classReader) {
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
}
