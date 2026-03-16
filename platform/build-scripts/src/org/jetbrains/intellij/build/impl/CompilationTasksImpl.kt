// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks

internal class CompilationTasksImpl(private val context: CompilationContext) : CompilationTasks {
  override suspend fun resolveProjectDependencies() {
    resolveProjectDependencies(context)
  }

  override suspend fun compileAllModulesAndTests() {
    context.compileModules(moduleNames = null, includingTestsInModules = null)
  }
}
