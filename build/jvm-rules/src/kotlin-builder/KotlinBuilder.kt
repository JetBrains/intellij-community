// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import io.opentelemetry.api.trace.Tracer
import org.jetbrains.bazel.jvm.ArgMap
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.WorkRequestReaderWithoutDigest
import org.jetbrains.bazel.jvm.processRequests
import java.io.Writer
import java.nio.file.Path

object KotlinBuildWorker : WorkRequestExecutor<WorkRequest> {
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    org.jetbrains.kotlin.cli.jvm.compiler.CompileEnvironmentUtil
    processRequests(startupArgs = startupArgs, executor = this, reader = WorkRequestReaderWithoutDigest(System.`in`), serviceName = "kotlin-builder")
  }

  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    val sources = request.inputPaths.asSequence()
      .filter { it.endsWith(".kt") || it.endsWith(".java") }
      .map { baseDir.resolve(it).normalize() }
      .toList()
    return buildKotlin(workingDir = baseDir, args = parseArgs(request.arguments), out = writer, sources = sources)
  }
}

// Important issues:
// https://youtrack.jetbrains.com/issue/KT-71680/Report-a-warning-when-Serializable-public-class-has-private-serializer
internal suspend fun buildKotlin(
  workingDir: Path,
  out: Writer,
  args: ArgMap<JvmBuilderFlags>,
  sources: List<Path>,
): Int {
  val compileContext = TraceHelper(isTracing = args.boolFlag(JvmBuilderFlags.TRACE))

  if (compileContext.isTracing) {
    out.appendLine("Worker Task Args: $args")
  }

  val info = createBuildInfo(args)
  when (info.platform) {
    Platform.JVM -> {
      compileContext.execute("compile classes") {
        val code = compileContext.execute("kotlinc") {
          compileKotlinForJvm(args = args, context = compileContext, sources = sources, out = out, baseDir = workingDir, info = info)
        }
        if (code != 0) {
          return code
        }
      }
    }

    Platform.UNRECOGNIZED -> throw IllegalStateException("unrecognized platform: $info")
  }
  compileContext.printTimings(out, targetLabel = info.label)
  return 0
}

private fun createBuildInfo(args: ArgMap<JvmBuilderFlags>): CompilationTaskInfo {
  val ruleKind = args.mandatorySingle(JvmBuilderFlags.RULE_KIND).split('_')
  check(ruleKind.size == 3 && ruleKind[0] == "kt") {
    "invalid rule kind $ruleKind"
  }

  return CompilationTaskInfo(
    label = args.mandatorySingle(JvmBuilderFlags.TARGET_LABEL),
    ruleKind = checkNotNull(RuleKind.valueOf(ruleKind[2].uppercase())) {
      "unrecognized rule kind ${ruleKind[2]}"
    },
    platform = checkNotNull(Platform.valueOf(ruleKind[1].uppercase())) {
      "unrecognized platform ${ruleKind[1]}"
    },
    moduleName = args.mandatorySingle(JvmBuilderFlags.KOTLIN_MODULE_NAME),
  )
}

