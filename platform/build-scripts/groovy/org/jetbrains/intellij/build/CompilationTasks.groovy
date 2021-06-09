// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.impl.CompilationTasksImpl

@CompileStatic
abstract class CompilationTasks {
  abstract void compileAllModulesAndTests()

  abstract void compileModules(@NotNull Collection<String> moduleNames, List<String> includingTestsInModules = [])

  abstract void buildProjectArtifacts(Collection<String> artifactNames)

  abstract void resolveProjectDependencies()

  abstract void resolveProjectDependenciesAndCompileAll()

  abstract void reuseCompiledClassesIfProvided()

  static CompilationTasks create(CompilationContext context) {
    return new CompilationTasksImpl(context)
  }
}
