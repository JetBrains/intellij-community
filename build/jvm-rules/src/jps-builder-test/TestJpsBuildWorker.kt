// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.jps

import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.TestModules
import org.jetbrains.bazel.jvm.collectSources
import org.jetbrains.bazel.jvm.getTestWorkerPaths
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.logging.LogWriter
import org.jetbrains.bazel.jvm.performTestInvocation
import java.io.PrintStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.ExperimentalPathApi

internal object TestJpsBuildWorker {
  @OptIn(ExperimentalPathApi::class)
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    val testPaths = getTestWorkerPaths()
    val baseDir = testPaths.baseDir
    //val sources = collectSources(sourceDirPath = "platform/platform-impl/src", paths = testPaths)

    val testModule = TestModules.PLATFORM_IMPL
    val sources = collectSources(sourceDirPath = testModule.sourcePath, paths = testPaths)
    val testParams = testModule.getParams(baseDir)

    performTestInvocation { out, coroutineScope ->
      // IDEA console is bad and outdated, write to file and use modern tooling to view logs
      // ${dateTimeFormatter.format(LocalDateTime.now())}
      val logFile = testPaths.userHomeDir.resolve("kotlin-worker/log.ndjson")
      configureGlobalJps(LogWriter(coroutineScope, PrintStream(Files.newOutputStream(logFile)), closeWriterOnShutdown = true))

      val args = parseArgs(testParams.lines().toTypedArray())
      val messageDigest = MessageDigest.getInstance("SHA-256")
      RootAllocator(Long.MAX_VALUE).use { allocator ->
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
        )
      }
    }
  }
}