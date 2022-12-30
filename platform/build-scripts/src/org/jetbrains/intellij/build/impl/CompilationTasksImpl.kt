// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.compilation.CompiledClasses
import org.jetbrains.jps.model.artifact.JpsArtifactService
import java.nio.file.Files
import java.nio.file.Path

class CompilationTasksImpl(private val context: CompilationContext) : CompilationTasks {
  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    resolveProjectDependencies()
    spanBuilder("compile modules").useWithScope {
      CompiledClasses.reuseOrCompile(context, moduleNames, includingTestsInModules)
    }
  }

  override fun buildProjectArtifacts(artifactNames: Set<String>) {
    if (artifactNames.isEmpty()) {
      return
    }

    val jps = JpsCompilationRunner(context)
    if (!context.options.useCompiledClassesFromProjectOutput) {
      compileModules(jps.getModulesIncludedInArtifacts(artifactNames))
    }

    spanBuilder("build project artifacts")
      .setAttribute(AttributeKey.stringArrayKey("artifactNames"), artifactNames.toList())
      .useWithScope {
        jps.buildArtifacts(artifactNames, buildIncludedModules = false)
        JpsArtifactService.getInstance().getArtifacts(context.project).asSequence()
          .filter { artifactNames.contains(it.name) }.forEach {
            if (it.outputFilePath?.let(Path::of)?.let(Files::exists) == true) {
              context.messages.info("${it.name} was successfully built at ${it.outputFilePath}")
            }
            else {
              context.messages.error("${it.name} is expected to be built at ${it.outputFilePath}")
            }
          }
      }
  }

  override fun resolveProjectDependencies() {
    if (context.compilationData.projectDependenciesResolved) {
      Span.current().addEvent("project dependencies are already resolved")
    }
    else {
      spanBuilder("resolve project dependencies").useWithScope {
        JpsCompilationRunner(context).resolveProjectDependencies()
      }
    }
  }

  override fun compileAllModulesAndTests() {
    compileModules(moduleNames = null, includingTestsInModules = null)
  }
}