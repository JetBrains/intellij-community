// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.contentModule

import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.WorkRequestReaderWithoutDigest
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.intellij.build.io.JarMergeSource
import org.jetbrains.intellij.build.io.defaultLibrarySourcesNamesFilter
import org.jetbrains.intellij.build.io.defaultModuleOutputNamesFilter
import org.jetbrains.intellij.build.io.mergeIntoJar
import java.io.Writer
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readLines
import kotlin.system.exitProcess

/**
 * Packs the `lib/` jars of a product's content modules, one jar per module, from already-built module and library jars.
 *
 * This exists so that packing a content-module jar does not require the product layout. The recipe - which module
 * outputs and which library jars a jar holds - is decided by the caller and arrives as a flat list; nothing here reads
 * a project model, a `ProductProperties`, or a plugin descriptor. That is what lets each jar be its own Bazel action
 * with only its own inputs declared, so an unrelated `.iml` edit cannot invalidate it.
 *
 * It is a persistent worker because the work per jar is a byte copy - every input entry is stored, not deflated - and
 * at several hundred jars a fresh JVM per jar would cost more than the copying.
 */
internal class ContentModulePackerWorker : WorkRequestExecutor {
  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    return packAll(arguments = request.arguments.asList(), writer = writer, baseDir = baseDir)
  }

  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      if (startupArgs.contains("--persistent_worker")) {
        processRequests(
          startupArgs = startupArgs,
          serviceName = "content-module-packer",
          reader = WorkRequestReaderWithoutDigest(System.`in`),
          executorFactory = { _, _ -> ContentModulePackerWorker() },
        )
        return
      }

      // One shot. Bazel runs a worker-enabled action without a worker under some strategies, and a packing run that
      // can be reproduced from a command line is what makes a "does this still produce the same jar?" check possible
      // without speaking the worker protocol.
      val writer = System.err.writer()
      val exitCode = runBlocking {
        packAll(arguments = startupArgs.toList(), writer = writer, baseDir = Path.of("").toAbsolutePath())
      }
      writer.flush()
      exitProcess(exitCode)
    }
  }
}

/** Packs every jar the flag file names, concurrently; returns the process/request exit code. */
internal suspend fun packAll(arguments: List<String>, writer: Writer, baseDir: Path): Int {
  val flagFile = when (arguments.size) {
    1 -> baseDir.resolve(arguments[0].removePrefixOrFail("--flagfile="))
    else -> {
      writer.appendLine("ERROR: expected a single `--flagfile=` argument, got '${arguments.joinToString()}'")
      return 3
    }
  }

  val jars = try {
    parseJarSpecs(flagFile.readLines(), baseDir)
  }
  catch (e: IllegalArgumentException) {
    writer.appendLine("ERROR: ${e.message}")
    return 3
  }

  return try {
    coroutineScope {
      jars.map { spec ->
        async(Dispatchers.IO) {
          runInterruptible {
            spec.output.createParentDirectories()
            val duplicates = mergeIntoJar(target = spec.output, sources = spec.sources, keepManifest = spec.keepManifest)
            if (duplicates.isNotEmpty()) {
              // Not a failure: two libraries merged into one jar can legitimately carry the same service file or
              // licence stub, and the first source wins. Reported so a genuine collision - two module outputs both
              // providing a class - is visible in the action log instead of silently resolving to one of them.
              synchronized(writer) {
                writer.appendLine(
                  "${spec.output.fileName}: ${duplicates.size} duplicate entr${if (duplicates.size == 1) "y" else "ies"}," +
                  " first source wins: ${duplicates.take(10).joinToString()}"
                )
              }
            }
          }
        }
      }.awaitAll()
    }
    0
  }
  catch (e: Throwable) {
    writer.appendLine(e.stackTraceToString())
    1
  }
}

private class JarSpec(
  @JvmField val output: Path,
  @JvmField val sources: List<JarMergeSource>,
  @JvmField val keepManifest: Boolean,
)

/**
 * Reads the flag file: one `output=` line per jar, followed by the `module=` and `library=` lines it is built from.
 *
 * A flag file rather than plain arguments because a product packs hundreds of jars from thousands of inputs, which
 * does not fit a command line. `output=` starts a group, so the file is ordered and the order is the precedence
 * `mergeIntoJar` uses for duplicates - emit every `library=` of a group before its `module=` lines, which is the order
 * `JarPackager` writes. An optional `keep-manifest=true` line inside a group lets that jar keep its
 * `META-INF/MANIFEST.MF`; the default is to drop it.
 */
private fun parseJarSpecs(lines: List<String>, baseDir: Path): List<JarSpec> {
  val result = ArrayList<JarSpec>()
  var output: Path? = null
  var sources = ArrayList<JarMergeSource>()
  var keepManifest = false

  fun flush() {
    val current = output ?: return
    require(sources.isNotEmpty()) { "no inputs for '$current'" }
    result.add(JarSpec(output = current, sources = sources, keepManifest = keepManifest))
  }

  for (line in lines) {
    if (line.isBlank()) {
      continue
    }
    val (option, value) = line.split('=', limit = 2).let {
      require(it.size == 2) { "expected `option=value`, got '$line'" }
      it[0] to it[1]
    }
    when (option) {
      "output" -> {
        flush()
        output = baseDir.resolve(value)
        sources = ArrayList()
        keepManifest = false
      }
      "keep-manifest" -> {
        requireNotNull(output) { "`keep-manifest=$value` before any `output=`" }
        keepManifest = value.toBooleanStrict()
      }
      "module" -> {
        requireNotNull(output) { "`module=$value` before any `output=`" }
        sources.add(JarMergeSource(file = baseDir.resolve(value), nameFilter = ::defaultModuleOutputNamesFilter))
      }
      "library" -> {
        requireNotNull(output) { "`library=$value` before any `output=`" }
        sources.add(JarMergeSource(file = baseDir.resolve(value), nameFilter = ::defaultLibrarySourcesNamesFilter))
      }
      else -> throw IllegalArgumentException("unknown option '$option' in '$line'")
    }
  }
  flush()

  val byOutput = HashMap<Path, Int>()
  for ((index, spec) in result.withIndex()) {
    val previous = byOutput.put(spec.output, index)
    require(previous == null) { "'${spec.output}' is declared twice, at group $previous and $index" }
  }
  return result
}

private fun String.removePrefixOrFail(prefix: String): String {
  val result = removePrefix(prefix)
  require(result != this) { "expected a value starting with '$prefix', got '$this'" }
  return result
}
