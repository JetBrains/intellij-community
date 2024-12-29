// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.cli.jvm.compiler

import org.jetbrains.bazel.jvm.kotlin.TraceHelper
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.ProjectId
import java.io.File
import java.io.PrintWriter
import java.io.Writer
import java.util.UUID

private val kotlinProjectId = ProjectId.ProjectUUID(UUID.randomUUID())

@Suppress("unused")
@OptIn(ExperimentalBuildToolsApi::class)
private fun incrementalCompilation(
  context: TraceHelper,
  sources: List<File>,
  args: List<String>,
  out: Writer,
): Int {
  val service = CompilationService.loadImplementation(context::class.java.classLoader)
  val strategyConfig = service.makeCompilerExecutionStrategyConfiguration()
  val compilationConfig = service
    .makeJvmCompilationConfiguration()
    .useLogger(WorkerKotlinLogger(out = out, isDebugEnabled = context.isTracing))
  val result = service.compileJvm(
    projectId = kotlinProjectId,
    strategyConfig = strategyConfig,
    compilationConfig = compilationConfig,
    sources = sources,
    arguments = args,
  )
  return result.ordinal
}

private class WorkerKotlinLogger(private val out: Writer, override val isDebugEnabled: Boolean) : KotlinLogger {
  override fun error(msg: String, throwable: Throwable?) {
    out.appendLine(msg)
    throwable?.let {
      PrintWriter(out).use {
        throwable.printStackTrace(it)
      }
    }
  }

  override fun warn(msg: String) {
    out.append("WARN: ").appendLine(msg)
  }

  override fun info(msg: String) {
    out.append("INFO: ").appendLine(msg)
  }

  override fun debug(msg: String) {
    if (isDebugEnabled) {
      out.append("DEBUG: ").appendLine(msg)
    }
  }

  override fun lifecycle(msg: String) {
    out.appendLine(msg)
  }
}