// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.kotlin.builder.wasmjs

import io.opentelemetry.api.trace.Tracer
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.WorkRequestReader
import org.jetbrains.bazel.jvm.doReadWorkRequestFromStream
import org.jetbrains.bazel.jvm.processRequests
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import java.io.File
import java.io.InputStream
import java.io.Writer
import java.nio.file.Path
import kotlin.system.exitProcess

internal class WasmJsBuildWorker: WorkRequestExecutor {
  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    val args = when (request.arguments.size) {
      1 -> Path(request.arguments[0].removePrefixStrict("--flagfile="))
      else -> {
        System.err.println("ERROR: must specify an argfile using `--flagfile=` as only argument, got '${request.arguments}'")
        return 3
      }
    }.readLines().normalizeCompilerArgs(baseDir)
    return try {
      K2JSCompiler.main(args.toTypedArray())
      0
    } catch (e: Throwable) {
      e.printStackTrace()
      1
    }
  }

  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      processRequests(
        startupArgs = startupArgs,
        executorFactory = { tracer, scope ->
          WasmJsBuildWorker()
        },
        reader = WorkRequestWithDigestReader(System.`in`),
        serviceName = "kotlin-builder-wasmjs",
      )
    }
  }
}

private class WorkRequestWithDigestReader(
  private val input: InputStream,
) : WorkRequestReader {
  override fun readWorkRequestFromStream(): WorkRequest? {
    return doReadWorkRequestFromStream(
      input = input,
      shouldReadDigest = true,
    )
  }
}

private fun String.removePrefixStrict(prefix: String): String {
  val result = removePrefix(prefix)
  check(result != this) {
    "String must start with $prefix but was: $this"
  }
  return result
}

// Arguments whose value is a `File.pathSeparator`-separated list of paths to resolve against the exec root.
//
// `-source-map-base-dirs` has to be absolute because `SourceFilePathResolver` compares its roots by path
// equality after `absoluteFile` alone, while normalizing the source files it resolves — so a relative `.`
// root becomes `<exec root>/.`, never matches any ancestor, and every source collapses to its bare file name.
private val PATH_LIST_ARGS = setOf("-libraries", "-source-map-base-dirs")

private fun List<String>.normalizeCompilerArgs(baseDir: Path): List<String> {

  // Windows hosts cannot locate the klib modules:
  // ```
  // java.lang.IllegalStateException: No module with C:\programdata\_bazel\dnzrtnud\execroot\_main\bazel-out\jvm-fastbuild\bin\external\community+\fleet\util\multiplatform\fleet.util.multiplatform_multiplatform_wasmjs.klib found
  // ```
  //
  // Internally, it seems that the Kotlin compiler compares with case-sensitivness even on case-insensitive filesystems
  // Path given:               C:\programdata\_bazel\dnzrtnud\execroot\_main\bazel-out\jvm-fastbuild\bin\external\community+\fleet\util\multiplatform\fleet.util.multiplatform_multiplatform_wasmjs.klib
  // `kotlinc`-expected path:  C:\ProgramData\_bazel\dnzrtnud\execroot\_main\bazel-out\jvm-fastbuild\bin\external\community+\fleet\util\multiplatform\fleet.util.multiplatform_multiplatform_wasmjs.klib
  //
  // As `toRealPath()` is expensive we only call it on `baseDir`, relative paths given by Bazel seems to respect proper case so far
  val realBaseDir = baseDir.toRealPath()

  return mapIndexed { index, arg ->
    when {
      arg.startsWith("-Xinclude=") -> "-Xinclude=${realBaseDir.resolveRelative(arg.removePrefix("-Xinclude="))}"
      getOrNull(index - 1) in PATH_LIST_ARGS -> arg.split(File.pathSeparatorChar).filter { path ->
        path.isNotBlank()
      }.joinToString(File.pathSeparator) {
        realBaseDir.resolveRelative(it)
      }
      else -> arg
    }
  }
}

private fun Path.resolveRelative(path: String): String {
  val candidate = Path(path)
  return when {
    candidate.isAbsolute -> candidate
    else -> resolve(candidate)
  }.normalize().pathString // using usual `\` separator on Windows hosts, kotlinc is happy with it
}
