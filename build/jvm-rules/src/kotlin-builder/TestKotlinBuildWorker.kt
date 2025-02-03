// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.bazel.jvm.TestModules
import org.jetbrains.bazel.jvm.collectSources
import org.jetbrains.bazel.jvm.getTestWorkerPaths
import org.jetbrains.bazel.jvm.performTestInvocation
import kotlin.io.path.ExperimentalPathApi

internal object TestKotlinBuildWorker {
  @OptIn(ExperimentalPathApi::class)
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    val testPaths = getTestWorkerPaths()
    val baseDir = testPaths.baseDir
    runBlocking(Dispatchers.Default) {
      val testModule = TestModules.XML_DOM
      val sources = collectSources(sourceDirPath = testModule.sourcePath, paths = testPaths)
      val testParams = testModule.getParams(baseDir)

      val args = parseArgs(testParams.trimStart().lines().toTypedArray())
      performTestInvocation { out, coroutineScope ->
       buildKotlin(workingDir = baseDir, out = out, args = args, sources = sources)
      }
    }
  }
}