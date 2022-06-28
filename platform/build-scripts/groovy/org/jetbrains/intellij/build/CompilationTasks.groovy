// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.impl.CompilationTasksImpl

@CompileStatic
abstract class CompilationTasks {
  abstract void compileAllModulesAndTests()

  abstract void compileModules(@Nullable Collection<String> moduleNames, List<String> includingTestsInModules = List.of())

  abstract void buildProjectArtifacts(Set<String> artifactNames)

  abstract void resolveProjectDependencies()

  abstract void resolveProjectDependenciesAndCompileAll()

  abstract void reuseCompiledClassesIfProvided()

  static CompilationTasks create(CompilationContext context) {
    return new CompilationTasksImpl(context)
  }
}
