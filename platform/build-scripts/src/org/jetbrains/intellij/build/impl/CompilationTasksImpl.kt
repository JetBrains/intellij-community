// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.compilation.CompiledClasses

class CompilationTasksImpl(private val context: CompilationContext) : CompilationTasks {
  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    resolveProjectDependencies()
    context.messages.block("Compiling modules") {
      CompiledClasses.reuseOrCompile(context, moduleNames, includingTestsInModules)
    }
  }

  override fun buildProjectArtifacts(artifactNames: Set<String>) {
    if (artifactNames.isNotEmpty()) {
      val jps = JpsCompilationRunner(context)
      if (!context.options.useCompiledClassesFromProjectOutput) {
        compileModules(jps.getModulesIncludedInArtifacts(artifactNames))
      }
      context.messages.block("Building project artifacts $artifactNames") {
        jps.buildArtifacts(artifactNames, buildIncludedModules = false)
      }
    }
  }

  override fun resolveProjectDependencies() {
    if (context.compilationData.projectDependenciesResolved) {
      Span.current().addEvent("project dependencies are already resolved")
    }
    else {
      context.messages.block("Resolving project dependencies") {
        JpsCompilationRunner(context).resolveProjectDependencies()
      }
    }
  }

  override fun compileAllModulesAndTests() {
    compileModules(moduleNames = null, includingTestsInModules = null)
  }
}