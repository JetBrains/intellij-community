// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScopeBlocking
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.compilation.CompiledClasses

class CompilationTasksImpl(private val context: CompilationContext) : CompilationTasks {
  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    resolveProjectDependencies()
    spanBuilder("compile modules").useWithScopeBlocking {
      CompiledClasses.reuseOrCompile(context, moduleNames, includingTestsInModules)
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
        jps.buildArtifacts(artifactNames, buildIncludedModules = false)
      }
  }

  override fun resolveProjectDependencies() {
    if (context.compilationData.projectDependenciesResolved) {
      Span.current().addEvent("project dependencies are already resolved")
    }
    else {
      spanBuilder("resolve project dependencies").useWithScopeBlocking {
        JpsCompilationRunner(context).resolveProjectDependencies()
      }
    }
  }

  override fun generateRuntimeModuleRepository() {
    if (context.compilationData.runtimeModuleRepositoryGenerated) {
      Span.current().addEvent("runtime module repository is already generated")
    }
    else {
      spanBuilder("generate runtime module repository").useWithScopeBlocking {
        JpsCompilationRunner(context).generateRuntimeModuleRepository()
      }
    }
  }

  override fun compileAllModulesAndTests() {
    compileModules(moduleNames = null, includingTestsInModules = null)
  }
}