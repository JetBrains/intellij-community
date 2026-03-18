// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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
 * The first caller computes the value in its own coroutine instead of starting a detached global job. This keeps failures and
 * cancellation inside the owning build flow and turns recursive waits into an immediate error instead of a hang.
 */
fun <T> suspendingLazy(coroutineName: String, initializer: suspend CoroutineScope.() -> T): SuspendingLazy<T> {
  return SuspendingLazyImpl(coroutineName = coroutineName, initializer = initializer)
}

private class ActiveSingleFlightComputations(
  private val activeOwners: Set<Any>,
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<ActiveSingleFlightComputations>

  fun contains(owner: Any): Boolean = owner in activeOwners

  fun add(owner: Any): ActiveSingleFlightComputations = ActiveSingleFlightComputations(activeOwners + owner)
}

internal fun singleFlightComputationContext(currentContext: CoroutineContext, owner: Any): CoroutineContext {
  val activeComputations = currentContext[ActiveSingleFlightComputations]
  return activeComputations?.add(owner) ?: ActiveSingleFlightComputations(setOf(owner))
}

internal fun checkRecursiveSingleFlightAwait(
  currentContext: CoroutineContext,
  owner: Any,
  operationName: String,
  deferred: CompletableDeferred<*>,
) {
  check(deferred.isCompleted || currentContext[ActiveSingleFlightComputations]?.contains(owner) != true) {
    "Recursive await of '$operationName' detected"
  }
}

private class SuspendingLazyImpl<T>(
  private val coroutineName: String,
  private val initializer: suspend CoroutineScope.() -> T,
) : SuspendingLazy<T> {
  private val lock = Mutex()
  private val owner = Any()

  @Volatile
  private var deferred: CompletableDeferred<T>? = null

  override suspend fun await(): T {
    val currentContext = currentCoroutineContext()
    deferred?.let {
      checkRecursiveSingleFlightAwait(currentContext, owner, coroutineName, it)
      return it.await()
    }

    val (actualDeferred, isOwner) = lock.withLock {
      deferred?.let {
        return@withLock it to false
      }

      val created = CompletableDeferred<T>()
      deferred = created
      created to true
    }

    if (!isOwner) {
      checkRecursiveSingleFlightAwait(currentContext, owner, coroutineName, actualDeferred)
      return actualDeferred.await()
    }

    try {
      actualDeferred.complete(
        withContext(CoroutineName(coroutineName) + singleFlightComputationContext(currentContext, owner)) {
          coroutineScope {
            initializer(this)
          }
        }
      )
    }
    catch (t: Throwable) {
      actualDeferred.completeExceptionally(t)
    }
    return actualDeferred.await()
  }
}
