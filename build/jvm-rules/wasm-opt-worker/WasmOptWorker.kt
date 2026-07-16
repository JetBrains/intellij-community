// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasm.opt

import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.WorkRequestReaderWithoutDigest
import org.jetbrains.bazel.jvm.processRequests
import java.io.IOException
import java.io.Writer
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.walk

/**
 * Runs binaryen's `wasm-opt` over every `.wasm` file of a linked WasmJS directory, copying all other
 * files through unchanged, and emits a `--symbolmap` per wasm file, named `<wasm basename>.txt` (used to
 * decode optimized wasm stacktraces). See `rules/opt-wasmjs.bzl`.
 */
internal class WasmOptWorker : WorkRequestExecutor {
  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    val flagfile = when (request.arguments.size) {
      1 -> baseDir.resolve(request.arguments[0].removePrefixStrict("--flagfile="))
      else -> {
        writer.appendLine("ERROR: must specify an argfile using `--flagfile=` as only argument, got '${request.arguments.joinToString()}'")
        return 3
      }
    }

    val valuesByOption = flagfile.readLines()
      .filter { it.isNotBlank() }
      .map { it.split('=', limit = 2) }
      .groupBy({ (option, _) -> option }, { (_, value) -> value })

    fun requiredPath(option: String): Path =
      baseDir.resolve(requireNotNull(valuesByOption[option]?.last()) { "$option is required" })

    return try {
      optimizeDirectory(
        inputDirectory = requiredPath("--input-directory"),
        outputDirectory = requiredPath("--output-directory"),
        functionsMapDirectory = requiredPath("--functions-map-directory"),
        wasmOpt = requiredPath("--wasm-opt"),
        wasmOptArgs = valuesByOption["--wasm-opt-arg"].orEmpty(),
      )
      0
    }
    catch (e: Throwable) {
      writer.appendLine(e.stackTraceToString())
      1
    }
  }

  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      processRequests(
        startupArgs = startupArgs,
        serviceName = "wasm-opt-worker",
        reader = WorkRequestReaderWithoutDigest(System.`in`),
        executorFactory = { _, _ -> WasmOptWorker() },
      )
    }
  }
}

@OptIn(ExperimentalPathApi::class)
private suspend fun optimizeDirectory(
  inputDirectory: Path,
  outputDirectory: Path,
  functionsMapDirectory: Path,
  wasmOpt: Path,
  wasmOptArgs: List<String>,
) {
  val (wasmFiles, otherFiles) = inputDirectory.walk()
    .map { it to outputDirectory.resolve(inputDirectory.relativize(it).toString()) }
    .toList()
    .partition { (file, _) -> file.name.endsWith(".wasm") }

  (wasmFiles + otherFiles).forEach { (_, target) -> target.createParentDirectories() }

  // passthrough any non .wasm file
  otherFiles.forEach { (file, target) -> file.copyTo(target, overwrite = true) }

  coroutineScope {
    wasmFiles.map { (input, output) ->
      async(Dispatchers.IO) {
        runInterruptible {
          optimize(
            input = input,
            output = output,
            functionsMapDirectory = functionsMapDirectory,
            wasmOpt = wasmOpt,
            wasmOptArgs = wasmOptArgs,
          )
        }
      }
    }.awaitAll()
  }
}

private fun optimize(input: Path, output: Path, functionsMapDirectory: Path, wasmOpt: Path, wasmOptArgs: List<String>) {
  val name = input.name
  val functionsMap = functionsMapDirectory.resolve(name.removeSuffix(".wasm") + ".txt")
  val command = buildList {
    add(wasmOpt.toString())
    addAll(wasmOptArgs)
    add(input.toString())
    add("-o")
    add(output.toString())
    add("--symbolmap=$functionsMap")
  }
  val process = ProcessBuilder(command).redirectErrorStream(true).start()
  val processOutput = process.inputStream.readAllBytes()
  val exitCode = process.waitFor()
  if (exitCode != 0) {
    throw IOException("wasm-opt failed on $name with exit code $exitCode:\n${String(processOutput)}")
  }
}

private fun String.removePrefixStrict(prefix: String): String {
  val result = removePrefix(prefix)
  check(result != this) {
    "String must start with $prefix but was: $this"
  }
  return result
}
