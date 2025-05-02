// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package org.jetbrains.bazel.jvm.worker.test

import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.TestModules
import org.jetbrains.bazel.jvm.TestWorkerPaths
import org.jetbrains.bazel.jvm.collectIjMonorepoSources
import org.jetbrains.bazel.jvm.collectTestDataSources
import org.jetbrains.bazel.jvm.configureOpenTelemetry
import org.jetbrains.bazel.jvm.getTestDataWorkerPaths
import org.jetbrains.bazel.jvm.getTestWorkerPaths
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.performTestInvocation
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.util.toScatterMap
import org.jetbrains.bazel.jvm.worker.buildUsingJps
import org.jetbrains.bazel.jvm.worker.configureGlobalJps
import org.jetbrains.bazel.jvm.worker.dependencies.DependencyAnalyzer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.ExperimentalPathApi

internal object TestJpsBuildWorker {
  @OptIn(ExperimentalPathApi::class)
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    val testModule = TestModules.TEST_AM_B

    val testPaths: TestWorkerPaths

    val sources: List<Path>

    @Suppress("KotlinConstantConditions")
    if (testModule.ordinal >= TestModules.TEST_AM_B.ordinal) {
      testPaths = getTestDataWorkerPaths()
      sources = testModule.sourcePaths.flatMap { collectTestDataSources(sourceDirPath = it, paths = testPaths) }
    }
    else {
      testPaths = getTestWorkerPaths()
      sources = testModule.sourcePaths.flatMap { collectIjMonorepoSources(sourceDirPath = it, paths = testPaths) }
    }

    require(sources.isNotEmpty()) { "No sources found" }

    val testParams = testModule.getParams(testPaths)
    val baseDir = testPaths.baseDir

    performTestInvocation { out, coroutineScope ->
      // IDEA console is bad and outdated, write to file and use modern tooling to view logs
      val logFile = testPaths.userHomeDir.resolve("kotlin-worker/log.jsonl")
      Files.createDirectories(logFile.parent)
      val openTelemetryAndOnClose = configureOpenTelemetry(Files.newOutputStream(logFile), "test-builder")
      val tracer = openTelemetryAndOnClose.first.getTracer("test-builder")
      configureGlobalJps(tracer, coroutineScope)

      val args = parseArgs(testParams.lines().toTypedArray())

      println("out jar: " + baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).normalize())

      val messageDigest = MessageDigest.getInstance("SHA-512")
      val exitCode = RootAllocator(Long.MAX_VALUE).use { allocator ->
        tracer.span("build") { span ->
          buildUsingJps(
            forceIncremental = true,
            baseDir = baseDir,
            args = args,
            out = out,
            sources = sources,
            dependencyFileToDigest = args.optionalList(JvmBuilderFlags.CP).toScatterMap { item, map ->
              val file = baseDir.resolve(item).normalize()
              val digest = messageDigest.digest(Files.readAllBytes(file))
              messageDigest.reset()
              map.put(file, digest)
            },
            sourceFileToDigest = sources.toScatterMap { item, map ->
              val file = baseDir.resolve(item).normalize()
              val digest = messageDigest.digest(Files.readAllBytes(file))
              messageDigest.reset()
              map.put(file, digest)
            },
            isDebugEnabled = true,
            allocator = allocator,
            parentSpan = span,
            tracer = tracer,
            cachePrefix = "test-builder-",
            dependencyAnalyzer = DependencyAnalyzer(coroutineScope),
          )
        }
      }

      openTelemetryAndOnClose.second()

      exitCode
    }
  }
}