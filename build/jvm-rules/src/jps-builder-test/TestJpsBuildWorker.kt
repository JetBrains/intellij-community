// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package org.jetbrains.bazel.jvm.jps.test

import io.opentelemetry.context.Context
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.TestModules
import org.jetbrains.bazel.jvm.collectSources
import org.jetbrains.bazel.jvm.configureOpenTelemetry
import org.jetbrains.bazel.jvm.getTestWorkerPaths
import org.jetbrains.bazel.jvm.jps.buildUsingJps
import org.jetbrains.bazel.jvm.jps.configureGlobalJps
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.performTestInvocation
import org.jetbrains.bazel.jvm.use
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.ExperimentalPathApi

internal object TestJpsBuildWorker {
  @OptIn(ExperimentalPathApi::class)
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    val testPaths = getTestWorkerPaths()
    val baseDir = testPaths.baseDir

    val testModule = TestModules.PLATFORM_IMPL
    val sources = collectSources(sourceDirPath = testModule.sourcePath, paths = testPaths)
    val testParams = testModule.getParams(baseDir)

    performTestInvocation { out, coroutineScope ->
      // IDEA console is bad and outdated, write to file and use modern tooling to view logs
      // ${dateTimeFormatter.format(LocalDateTime.now())}
      val logFile = testPaths.userHomeDir.resolve("kotlin-worker/log.jsonl")
      Files.createDirectories(logFile.parent)
      val tracer = configureOpenTelemetry(Files.newOutputStream(logFile), "test-builder").getTracer("test-builder")
      configureGlobalJps(tracer, coroutineScope)

      val args = parseArgs(testParams.lines().toTypedArray())
      val messageDigest = MessageDigest.getInstance("SHA-512")
      RootAllocator(Long.MAX_VALUE).use { allocator ->
        tracer.spanBuilder("build").use { span ->
          buildUsingJps(
            baseDir = baseDir,
            args = args,
            out = out,
            sources = sources,
            dependencyFileToDigest = args.optionalList(JvmBuilderFlags.CLASSPATH).associate {
              val file = baseDir.resolve(it).normalize()
              val digest = messageDigest.digest(Files.readAllBytes(file))
              messageDigest.reset()
              file to digest
            },
            sourceFileToDigest = sources.associate {
              val file = baseDir.resolve(it).normalize()
              val digest = messageDigest.digest(Files.readAllBytes(file))
              messageDigest.reset()
              file to digest
            },
            isDebugEnabled = true,
            allocator = allocator,
            tracingContext = Context.current(),
            parentSpan = span,
            tracer = tracer,
            cachePrefix = "test-builder-",
          )
        }
      }
    }
  }
}