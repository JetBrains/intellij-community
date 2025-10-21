// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use

internal class CompilationTasksImpl(private val context: CompilationContext) : CompilationTasks {
  override suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    context.compileModules(moduleNames, includingTestsInModules)
  }

  override suspend fun buildProjectArtifacts(artifactNames: Set<String>) {
    if (artifactNames.isEmpty()) {
      return
    }

    val jps = JpsCompilationRunner(context)
    if (!context.options.useCompiledClassesFromProjectOutput) {
      context.compileModules(jps.getModulesIncludedInArtifacts(artifactNames))
    }

    spanBuilder("build project artifacts")
      .setAttribute(AttributeKey.stringArrayKey("artifactNames"), java.util.List.copyOf(artifactNames))
      .use {
        jps.buildArtifacts(artifactNames = artifactNames, buildIncludedModules = false)
      }
  }

  override suspend fun resolveProjectDependencies() {
    resolveProjectDependencies(context)
  }

  override suspend fun compileAllModulesAndTests() {
    context.compileModules(moduleNames = null, includingTestsInModules = null)
  }
}
