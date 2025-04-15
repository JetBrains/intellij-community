// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")
package org.jetbrains.bazel.jvm.worker

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.Logger
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.worker.dependencies.DependencyAnalyzer
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.kotlin.config.IncrementalCompilation
import java.io.Writer
import java.nio.file.Path

// Please note: for performance reasons, we do not set `jps.new.storage.compact.on.close` to true.
// As a result, the database file on disk may grow to some extent.

fun configureGlobalJps(tracer: Tracer, scope: CoroutineScope) {
  val globalSpanForIJLogger = tracer.spanBuilder("global").startSpan()
  scope.coroutineContext.job.invokeOnCompletion { globalSpanForIJLogger.end() }

  Logger.setFactory { BazelLogger(category = IdeaLogRecordFormatter.smartAbbreviate(it), span = globalSpanForIJLogger) }
  System.setProperty("jps.service.manager.impl", BazelJpsServiceManager::class.java.name)
  System.setProperty("jps.backward.ref.index.builder.fs.case.sensitive", "true")
  System.setProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, Runtime.getRuntime().availableProcessors().toString())
  System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true")
  System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
  System.setProperty(GlobalOptions.ALLOW_PARALLEL_AUTOMAKE_OPTION, "true")
  System.setProperty("idea.compression.enabled", "false")
  System.setProperty("jps.track.library.dependencies", "true")
  System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
}

internal class JpsBuildWorker private constructor(
  private val allocator: RootAllocator,
  coroutineScope: CoroutineScope,
) : WorkRequestExecutor<WorkRequestWithDigests> {
  private val dependencyAnalyzer = DependencyAnalyzer(coroutineScope)

  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      RootAllocator(Long.MAX_VALUE).use { allocator ->
        processRequests(
          startupArgs = startupArgs,
          executorFactory = { tracer, scope ->
            configureGlobalJps(tracer = tracer, scope = scope)
            JpsBuildWorker(allocator, scope)
          },
          reader = WorkRequestWithDigestReader(System.`in`),
          serviceName = "jvm-builder",
        )
      }
    }
  }

  override suspend fun execute(request: WorkRequestWithDigests, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    return incrementalBuild(
      request = request,
      baseDir = baseDir,
      tracer = tracer,
      writer = writer,
      allocator = allocator,
      dependencyAnalyzer = dependencyAnalyzer,
    )
  }
}