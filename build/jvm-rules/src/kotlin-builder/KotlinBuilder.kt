// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.google.devtools.build.lib.worker.WorkerProtocol
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.processRequests
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

private val FLAG_FILE_RE = Pattern.compile("""^--flagfile=((.*)-(\d+).params)$""").toRegex()

object KotlinBuildWorker : WorkRequestExecutor {
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    org.jetbrains.kotlin.cli.jvm.compiler.CompileEnvironmentUtil
    processRequests(startupArgs, this, debugLogClassifier = "kotlin")
  }

  override suspend fun execute(request: WorkerProtocol.WorkRequest, writer: Writer, baseDir: Path): Int {
    val sources = request.inputsList.asSequence()
      .filter { it.path.endsWith(".kt") || it.path.endsWith(".java") }
      .map { baseDir.resolve(it.path) }
      .toList()
    return buildKotlin(workingDir = baseDir, args = request.argumentsList, out = writer, sources = sources)
  }
}

// Important issues:
// https://youtrack.jetbrains.com/issue/KT-71680/Report-a-warning-when-Serializable-public-class-has-private-serializer
private suspend fun buildKotlin(
  workingDir: Path,
  out: Writer,
  args: List<String>,
  sources: List<Path>,
): Int {
  check(args.isNotEmpty()) {
    "expected at least a single arg got: ${args.joinToString(" ")}"
  }

  val argMap = createArgMap(
    FLAG_FILE_RE.matchEntire(args[0])?.groups?.get(1)?.let {
      Files.readAllLines(Path.of(it.value))
    } ?: args,
    enumClass = KotlinBuilderFlags::class.java,
  )

  val compileContext = TraceHelper(isTracing = argMap.boolFlag(KotlinBuilderFlags.TRACE))

  if (compileContext.isTracing) {
    out.appendLine("Worker Task Args: $argMap")
  }

  val info = createBuildInfo(argMap)
  when (info.platform) {
    Platform.JVM -> {
      compileContext.execute("compile classes") {
        val code = compileContext.execute("kotlinc") {
          compileKotlinForJvm(args = argMap, context = compileContext, sources = sources, out = out, workingDir = workingDir, info = info)
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

private fun createBuildInfo(argMap: ArgMap<KotlinBuilderFlags>): CompilationTaskInfo {
  val ruleKind = argMap.mandatorySingle(KotlinBuilderFlags.RULE_KIND).split('_')
  check(ruleKind.size == 3 && ruleKind[0] == "kt") {
    "invalid rule kind $ruleKind"
  }

  return CompilationTaskInfo(
    label = argMap.mandatorySingle(KotlinBuilderFlags.TARGET_LABEL),
    ruleKind = checkNotNull(RuleKind.valueOf(ruleKind[2].uppercase())) {
      "unrecognized rule kind ${ruleKind[2]}"
    },
    platform = checkNotNull(Platform.valueOf(ruleKind[1].uppercase())) {
      "unrecognized platform ${ruleKind[1]}"
    },
    moduleName = argMap.mandatorySingle(KotlinBuilderFlags.KOTLIN_MODULE_NAME).also {
      check(it.isNotBlank()) { "--kotlin_module_name should not be blank" }
    },
  )
}

