// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")
package com.intellij.tools.build.bazel.jvmIncBuilder

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildContextImpl
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.bazel.jvm.*
import org.jetbrains.bazel.jvm.util.*
import java.io.InputStream
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern

internal class BazelIncExecutor : WorkRequestExecutor<WorkRequestWithDigests> {
  private val FLAG_FILE_RE: Regex = Pattern.compile("""^--flagfile=((.*)-(\d+).params)$""").toRegex()

  companion object {

    private fun configureLogging(tracer: Tracer, scope: CoroutineScope) {
      // todo
//      val globalSpanForIJLogger = tracer.spanBuilder("global").startSpan()
//      scope.coroutineContext.job.invokeOnCompletion { globalSpanForIJLogger.end() }
//      Logger.setFactory { BazelLogger(category = IdeaLogRecordFormatter.smartAbbreviate(it), span = globalSpanForIJLogger) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
      processRequests(
        startupArgs = args,
        executorFactory = { tracer, scope ->
          configureLogging(tracer, scope)
          return@processRequests BazelIncExecutor()
        },
        reader = WorkRequestWithDigestReader(System.`in`),
        serviceName = "jvm-inc-builder"
      )
    }
  }

  override suspend fun execute(request: WorkRequestWithDigests, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    val args: ArgMap<CLFlags> = parseArgs(request.arguments)
    val flagsMap = EnumMap<CLFlags, List<String>>(CLFlags::class.java)
    for (flag in CLFlags.entries) {
      args.optional(flag)?.let { flagsMap.put(flag, it) }
    }

    val exitCode = BazelIncBuilder().build(
      BuildContextImpl(baseDir, request.inputPaths.asIterable(), request.inputDigests.asIterable(), flagsMap, writer)
    )
    if (exitCode == ExitCode.CANCEL) {
      throw CancellationException()
    }
    return if (exitCode == ExitCode.OK) 0 else -1;
  }

  private fun parseArgs(args: Array<String>): ArgMap<CLFlags> {
    check(args.isNotEmpty()) {
      "expected at least a single arg got: ${args.joinToString(" ")}"
    }

    return createArgMap(
      args = FLAG_FILE_RE.matchEntire(args[0])?.groups?.get(1)?.let {
        Files.readAllLines(Path.of(it.value))
      } ?: args.asList(),
      enumClass = CLFlags::class.java,
    )
  }
}


internal class WorkRequestWithDigests(
  arguments: Array<String>,
  inputPaths: Array<String>,
  requestId: Int,
  cancel: Boolean,
  verbosity: Int,
  sandboxDir: String?,
  @JvmField val inputDigests: Array<ByteArray>,
) : WorkRequest(
  arguments = arguments,
  inputPaths = inputPaths,
  requestId = requestId,
  cancel = cancel,
  verbosity = verbosity,
  sandboxDir = sandboxDir,
)


internal open class WorkRequestWithDigestReader(
  //private val allocator: BufferAllocator,
  private val input: InputStream,
) : WorkRequestReader<WorkRequestWithDigests> {
  private val inputPathsToReuse = ArrayList<String>()
  private val inputDigestToReuse = ArrayList<ByteArray>()
  private val argListToReuse = ArrayList<String>()

  override fun readWorkRequestFromStream(): WorkRequestWithDigests? {
    val inputDigestToReuse = inputDigestToReuse
    inputDigestToReuse.clear()
    val result = doReadWorkRequestFromStream(
      input = input,
      inputPathsToReuse = inputPathsToReuse,
      argListToReuse = argListToReuse,
      readDigest = { codedInputStream, tag ->
        val digest = codedInputStream.readByteArray()
        //inputDigestToReuse.setSafe(counter++, digest)
        inputDigestToReuse.add(digest)
      },
      requestCreator = { argListToReuse, inputPathsToReuse, requestId, cancel, verbosity, sandboxDir ->
        //digests.setValueCount(counter)
        //counter = -1
        WorkRequestWithDigests(
          arguments = argListToReuse,
          inputPaths = inputPathsToReuse,
          requestId = requestId,
          cancel = cancel,
          verbosity = verbosity,
          sandboxDir = sandboxDir,
          inputDigests = inputDigestToReuse.toTypedArray(),
        )
      },
    )
    return result
  }

}

