// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import java.util.function.Predicate

fun CoroutineScope.createSkippableJob(
  spanBuilder: SpanBuilder,
  stepId: String,
  context: BuildContext,
  task: suspend () -> Unit,
): Job {
  return launch(CoroutineName("$stepId build step")) {
    context.executeStep(spanBuilder, stepId) {
      task()
    }
  }
}

/**
 * Filter is applied only to files, not to directories.
 */
fun copyDirWithFileFilter(fromDir: Path, targetDir: Path, fileFilter: Predicate<Path>) {
  copyDir(sourceDir = fromDir, targetDir = targetDir, fileFilter = fileFilter)
}

suspend fun zip(targetFile: Path, dir: Path, context: CompilationContext) {
  spanBuilder("pack")
    .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
    .use {
      org.jetbrains.intellij.build.io.zipWithPackageIndex(targetFile = targetFile, dir = dir)
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

interface SuspendingLazy<T> {
  suspend fun await(): T
}

/**
 * Computes a value on the first `await()` and shares the result with all concurrent awaiters.
 *
 * Cancellation evicts the in-flight computation so the next call retries, while successful values and ordinary failures are reused.
 */
fun <T> suspendingLazy(coroutineName: String, initializer: suspend CoroutineScope.() -> T): SuspendingLazy<T> {
  return AsyncCacheBackedSuspendingLazy(coroutineName = coroutineName, initializer = initializer)
}

private class AsyncCacheBackedSuspendingLazy<T>(
  private val coroutineName: String,
  private val initializer: suspend CoroutineScope.() -> T,
) : SuspendingLazy<T> {
  private val key = NamedSuspendingLazyKey(coroutineName)
  private val cache = AsyncCache<NamedSuspendingLazyKey, T>()

  override suspend fun await(): T {
    return cache.getOrPut(key) {
      withContext(CoroutineName(coroutineName)) {
        coroutineScope {
          initializer(this)
        }
      }
    }
  }
}

private class NamedSuspendingLazyKey(private val name: String) {
  override fun toString(): String = name
}
