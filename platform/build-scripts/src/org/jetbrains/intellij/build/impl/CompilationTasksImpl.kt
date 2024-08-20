// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.compilation.CompiledClasses
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.telemetry.useWithScope

internal class CompilationTasksImpl(private val context: CompilationContext) : CompilationTasks {
  private val compileMutex = Mutex()

  override suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    resolveProjectDependencies()
    spanBuilder("compile modules").use {
      compileMutex.withLock {
        CompiledClasses.reuseOrCompile(context = context, moduleNames = moduleNames, includingTestsInModules = includingTestsInModules)
      }
    }
  }

  override suspend fun buildProjectArtifacts(artifactNames: Set<String>) {
    if (artifactNames.isEmpty()) {
      return
    }

    val jps = JpsCompilationRunner(context)
    if (!context.options.useCompiledClassesFromProjectOutput) {
      compileModules(jps.getModulesIncludedInArtifacts(artifactNames))
    }

    spanBuilder("build project artifacts")
      .setAttribute(AttributeKey.stringArrayKey("artifactNames"), java.util.List.copyOf(artifactNames))
      .useWithScope {
        jps.buildArtifacts(artifactNames = artifactNames, buildIncludedModules = false)
      }
  }

  override suspend fun resolveProjectDependencies() {
    if (context.compilationData.projectDependenciesResolved) {
      Span.current().addEvent("project dependencies are already resolved")
    }
    else {
      spanBuilder("resolve project dependencies").use {
        JpsCompilationRunner(context).resolveProjectDependencies()
      }
    }
  }

  override suspend fun generateRuntimeModuleRepository() {
    if (context.compilationData.runtimeModuleRepositoryGenerated) {
      Span.current().addEvent("runtime module repository is already generated")
    }
    else {
      spanBuilder("generate runtime module repository").use {
        JpsCompilationRunner(context).generateRuntimeModuleRepository()
      }
    }
  }

  override suspend fun compileAllModulesAndTests() {
    compileModules(moduleNames = null, includingTestsInModules = null)
  }
}