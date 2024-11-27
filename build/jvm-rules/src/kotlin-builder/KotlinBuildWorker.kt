// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.google.devtools.build.lib.worker.WorkerProtocol
import org.jetbrains.bazel.jvm.WorkRequestHandler
import java.io.Writer
import java.nio.file.Path

object KotlinBuildWorker {
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    WorkRequestHandler({ workRequest, consoleOutput, baseDir ->
      handleRequest(workRequest, consoleOutput, baseDir)
    })
      .processRequests(startupArgs)
  }
}

private fun handleRequest(
  workRequest: WorkerProtocol.WorkRequest,
  consoleOutput: Writer,
  baseDir: Path,
): Int {
  val sources = workRequest.inputsList.asSequence()
    .filter { it.path.endsWith(".kt") || it.path.endsWith(".java") }
    .map { baseDir.resolve(it.path).toFile() }
    .toList()

  return buildKotlin(workingDir = baseDir, args = workRequest.argumentsList, out = consoleOutput, sources = sources)
}