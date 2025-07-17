// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")
package com.intellij.tools.build.bazel.jvmIncBuilder

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildContextImpl
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation.*
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.bazel.jvm.*
import org.jetbrains.bazel.jvm.util.*
import org.jetbrains.jps.javac.ExternalRefCollectorCompilerToolExtension
import org.jetbrains.kotlin.config.IncrementalCompilation
import java.io.InputStream
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.regex.Pattern

internal class BazelIncExecutor : WorkRequestExecutor {
  private val FLAG_FILE_RE: Regex = Pattern.compile("""^--flagfile=((.*)-(\d+).params)$""").toRegex()

  companion object {
    private val defaultLogLevel = Level.INFO

    private fun configureLogging(tracer: Tracer, scope: CoroutineScope) {
      val globalSpan = tracer.spanBuilder("global").startSpan()
      scope.coroutineContext.job.invokeOnCompletion { globalSpan.end() }

      // configure logging for the code using IJ Platform logging API
      com.intellij.openapi.diagnostic.Logger.setFactory { IJPrintStreamLogger(category = it, stream = System.err, span = globalSpan) }

      // configure logging for the code using Java SDK logging API
      val rootLogger = java.util.logging.Logger.getLogger("")
      rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
      LogManager.getLogManager().readConfiguration(".level=${defaultLogLevel.name}".byteInputStream())
      rootLogger.addHandler(
        PrintStreamLogHandler(defaultLogLevel, System.err, globalSpan)
      )
    }

    private fun configureGlobals() {
      ExternalRefCollectorCompilerToolExtension.enable()
      System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
      System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JS_PROPERTY, "true")
      System.setProperty("kotlin.jps.dumb.mode", "true")

      // TMH assertions
      System.setProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY, "true")
      System.setProperty(ThreadingModelInstrumenter.GENERATE_LINE_NUMBERS_PROPERTY, "true")
    }

    @JvmStatic
    fun main(args: Array<String>) {
      processRequests(
        startupArgs = args,
        executorFactory = { tracer, scope ->
          configureGlobals()
          configureLogging(tracer, scope)
          return@processRequests BazelIncExecutor()
        },
        reader = WorkRequestWithDigestReader(System.`in`),
        serviceName = "jvm-inc-builder"
      )
    }
  }

  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    val args: ArgMap<CLFlags> = parseArgs(request.arguments)
    val flagsMap = EnumMap<CLFlags, List<String>>(CLFlags::class.java)
    for (flag in CLFlags.entries) {
      args.optional(flag)?.let { flagsMap.put(flag, it) }
    }

    val exitCode = BazelIncBuilder().build(
      BuildContextImpl(baseDir, request.inputs.asIterable(), flagsMap, writer)
    )
    if (exitCode == ExitCode.CANCEL) {
      throw CancellationException()
    }
    return if (exitCode == ExitCode.OK) 0 else -1;
  }

  private fun parseArgs(args: Array<String>): ArgMap<CLFlags> {
    check(args.isNotEmpty()) {
      "expected at least a single arg got: ${args.joinToString(" ")}"
    }

    return createArgMap(
      args = FLAG_FILE_RE.matchEntire(args[0])?.groups?.get(1)?.let {
        Files.readAllLines(Path.of(it.value))
      } ?: args.asList(),
      enumClass = CLFlags::class.java,
    )
  }
}

internal open class WorkRequestWithDigestReader(
  //private val allocator: BufferAllocator,
  private val input: InputStream,
) : WorkRequestReader {
  override fun readWorkRequestFromStream(): WorkRequest? {
    return doReadWorkRequestFromStream(
      input = input,
      shouldReadDigest = true,
    )
  }
}
