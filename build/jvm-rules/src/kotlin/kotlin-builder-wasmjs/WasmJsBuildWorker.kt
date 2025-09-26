// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.kotlin.builder.wasmjs

import kotlin.io.path.Path
import kotlin.io.path.readLines
import org.jetbrains.kotlin.cli.js.K2JSCompiler

internal class WasmJsBuildWorker {
  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      val args = when (startupArgs.size) {
        1 -> Path(startupArgs[0].removePrefixStrict("--flagfile="))
        else -> error("must specify an argfile using `--flagfile=` as only argument, got '$startupArgs'")
      }.readLines()

      println("[WasmJsBuildWorker] Kotlin compiler args:\n${args.joinToString("\n")}")
      K2JSCompiler.main(args.toTypedArray())
    }
  }
}

private fun String.removePrefixStrict(prefix: String): String {
  val result = removePrefix(prefix)
  check(result != this) {
    "String must start with $prefix but was: $this"
  }
  return result
}
