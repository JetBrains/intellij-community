// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.copyDir
import java.nio.file.Path
import java.util.function.Predicate

internal fun span(spanBuilder: SpanBuilder, task: Runnable) {
  spanBuilder.use {
    task.run()
  }
}

inline fun CoroutineScope.createSkippableJob(spanBuilder: SpanBuilder,
                                             taskId: String,
                                             context: BuildContext,
                                             crossinline task: suspend () -> Unit): Job? {
  if (context.isStepSkipped(taskId)) {
    spanBuilder.startSpan().addEvent("skip").end()
    return null
  }
  else {
    return launch {
      spanBuilder.useWithScope {
        task()
      }
    }
  }
}

/**
 * Filter is applied only to files, not to directories.
 */
fun copyDirWithFileFilter(fromDir: Path, targetDir: Path, fileFilter: Predicate<Path>) {
  copyDir(sourceDir = fromDir, targetDir = targetDir, fileFilter = fileFilter)
}

fun zip(context: CompilationContext, targetFile: Path, dir: Path) {
  spanBuilder("pack")
    .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
    .use {
      org.jetbrains.intellij.build.io.zip(targetFile = targetFile, dirs = mapOf(dir to ""))
    }
}

/**
 * @return a list of JVM args for opened packages (JBR17+) in a format `--add-opens=PACKAGE=ALL-UNNAMED` for a specified or current OS
 */
internal fun getCommandLineArgumentsForOpenPackages(context: CompilationContext, target: OsFamily? = null): List<String> {
  val file = context.paths.communityHomeDir.resolve("platform/platform-impl/resources/META-INF/OpenedPackages.txt")
  val os = when (target) {
    OsFamily.WINDOWS -> OS.Windows
    OsFamily.MACOS -> OS.macOS
    OsFamily.LINUX -> OS.Linux
    null -> OS.CURRENT
  }
  return JavaModuleOptions.readOptions(file, os)
}
