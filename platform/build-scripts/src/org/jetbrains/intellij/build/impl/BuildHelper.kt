// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.Subtask
import org.jetbrains.intellij.build.TaskScope
import org.jetbrains.intellij.build.awaitShared
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.runBlockingOnVirtualThreads
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.file.Path
import java.util.function.Predicate

/** Forks a build step into the group. The subtask holds `null` when the step is skipped or fails. */
fun TaskScope.createSkippableJob(
  spanBuilder: SpanBuilder,
  stepId: String,
  context: BuildContext,
  task: suspend () -> Unit,
): Subtask<Unit?> {
  return fork("$stepId build step") {
    context.executeStep(spanBuilder, stepId) {
      task()
    }
  }
}

/**
 * Filter is applied only to files, not to directories.
 *
 * Returns the files that were written; see [copyDir].
 */
fun copyDirWithFileFilter(fromDir: Path, targetDir: Path, fileFilter: Predicate<Path>): List<Path> {
  return copyDir(sourceDir = fromDir, targetDir = targetDir, fileFilter = fileFilter)
}

fun zip(targetFile: Path, dir: Path, context: CompilationContext) {
  spanBuilder("pack")
    .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
    .blockingUse {
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
 * The computation runs on a virtual thread of its own, under the telemetry context of the caller that started it.
 * A caller that stops waiting changes nothing for the other callers. Successful values and ordinary failures are
 * reused.
 */
fun <T> suspendingLazy(coroutineName: String, initializer: suspend () -> T): SuspendingLazy<T> {
  return AsyncCacheBackedSuspendingLazy(coroutineName = coroutineName, initializer = initializer)
}

private class AsyncCacheBackedSuspendingLazy<T>(
  coroutineName: String,
  private val initializer: suspend () -> T,
) : SuspendingLazy<T> {
  private val key = NamedSuspendingLazyKey(coroutineName)
  private val cache = AsyncCache<NamedSuspendingLazyKey, T>()

  /** The computation of the cache carries the name of the lazy as its thread name. */
  override suspend fun await(): T {
    // the computation runs on a thread of its own, so a span of the initializer gets its parent from here
    val telemetryContext = Context.current().asContextElement()
    // the initializer still suspends, so it needs an entry of its own back into coroutines
    val load = { runBlockingOnVirtualThreads(telemetryContext) { initializer() } }
    // `awaitShared` and not `getOrPut`, so an awaiter stays cancellable and does not block its thread
    return cache.sharedFuture(key, load).awaitShared()
  }
}

private class NamedSuspendingLazyKey(private val name: String) {
  override fun toString(): String = name
}
