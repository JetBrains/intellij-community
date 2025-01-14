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
      val testModule = TestModules.PLATFORM_BOOTSTRAP
      val sources = collectSources(sourceDirPath = testModule.sourcePath, paths = testPaths)
      val testParams = testModule.getParams(baseDir)

      val args = parseArgs(testParams.trimStart().lines().toTypedArray())
      performTestInvocation { out, coroutineScope ->
       buildKotlin(workingDir = baseDir, out = out, args = args, sources = sources)
      }
    }
  }
}