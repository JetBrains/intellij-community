// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.ThrowableComputable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.io.path.writeText
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Suspends until it's possible to obtain the read lock and then
 * runs the [action] holding the lock **without** preventing write actions.
 * See [constrainedReadAction] for semantic details.
 *
 * @see readActionUndispatched
 * @see readActionBlocking
 */
suspend fun <T> readAction(action: () -> T): T {
  return constrainedReadAction(action = action)
}

/**
 * Suspends until it's possible to obtain the read lock in smart mode and then
 * runs the [action] holding the lock **without** preventing write actions.
 * See [constrainedReadAction] for semantic details.
 *
 * @see smartReadActionBlocking
 */
suspend fun <T> smartReadAction(project: Project, action: () -> T): T {
  return constrainedReadAction(ReadConstraint.inSmartMode(project), action = action)
}

/**
 * Runs given [action] under [read lock][com.intellij.openapi.application.Application.runReadAction]
 * **without** preventing write actions.
 *
 * The function suspends if at the moment of calling it's not possible to acquire the read lock,
 * or if [constraints] are not [satisfied][ReadConstraint.isSatisfied].
 * If the write action happens while the [action] is running, then the [action] is canceled,
 * and the function suspends until its possible to acquire the read lock, and then the [action] is tried again.
 *
 * Since the [action] might be executed several times, it must be idempotent.
 * The function returns when given [action] was completed fully.
 * To support cancellation, the [action] must regularly invoke [com.intellij.openapi.progress.ProgressManager.checkCanceled].
 *
 * The [action] is dispatched to [Dispatchers.Default], because a read action is expected to be a CPU-bound task.
 *
 * @see constrainedReadActionUndispatched
 * @see constrainedReadActionBlocking
 */
suspend fun <T> constrainedReadAction(vararg constraints: ReadConstraint, action: () -> T): T {
  return readWriteActionSupport().executeReadAction(constraints.toList(), action = action)
}

/**
 * See [constrainedReadActionUndispatched] for semantic details.
 *
 * @see readAction
 */
@IntellijInternalApi
@Internal
suspend fun <T> readActionUndispatched(action: () -> T): T {
  return constrainedReadActionUndispatched(action = action)
}

/**
 * Has same semantics as [constrainedReadAction],
 * except it runs the given [action] in the original [CoroutineDispatcher]
 * without dispatching it to [Dispatchers.Default].
 *
 * Use with care. This method should not be used to compute CPU-heavy stuff.
 */
@IntellijInternalApi
@Internal
suspend fun <T> constrainedReadActionUndispatched(vararg constraints: ReadConstraint, action: () -> T): T {
  return readWriteActionSupport().executeReadAction(constraints.toList(), undispatched = true, action = action)
}

/**
 * Suspends until it's possible to obtain the read lock and then
 * runs the [action] holding the lock and **preventing** write actions.
 * See [constrainedReadActionBlocking] for semantic details.
 *
 * @see readAction
 */
suspend fun <T> readActionBlocking(action: () -> T): T {
  return constrainedReadActionBlocking(action = action)
}

/**
 * Suspends until it's possible to obtain the read lock in smart mode and then
 * runs the [action] holding the lock and **preventing** write actions.
 * See [constrainedReadActionBlocking] for semantic details.
 *
 * @see smartReadAction
 */
suspend fun <T> smartReadActionBlocking(project: Project, action: () -> T): T {
  return constrainedReadActionBlocking(ReadConstraint.inSmartMode(project), action = action)
}

/**
 * Runs given [action] under [read lock][com.intellij.openapi.application.Application.runReadAction]
 * **preventing** write actions.
 *
 * The function suspends if at the moment of calling it's not possible to acquire the read lock,
 * or if [constraints] are not [satisfied][ReadConstraint.isSatisfied].
 * If the write action happens while the [action] is running, then the [action] is **not** canceled,
 * meaning the [action] will block pending write actions until finished.
 *
 * The function returns when given [action] was completed fully.
 * To support cancellation, the [action] must regularly invoke [com.intellij.openapi.progress.ProgressManager.checkCanceled].
 *
 * The [action] is dispatched to [Dispatchers.Default], because a read action is expected to be a CPU-bound task.
 *
 * @see constrainedReadAction
 */
suspend fun <T> constrainedReadActionBlocking(vararg constraints: ReadConstraint, action: () -> T): T {
  return readWriteActionSupport().executeReadAction(constraints.toList(), blocking = true, action = action)
}

sealed interface ReadResult<out R> {

  @Internal
  class Value<out V> internal constructor(val value: V) : ReadResult<V>

  @Internal
  class WriteAction<out V> internal constructor(val action: () -> V) : ReadResult<V>

  @Internal
  companion object : ReadAndWriteScope {

    @JvmStatic
    override fun <R> value(value: R): ReadResult<R> = Value(value)

    @JvmStatic
    override fun <R> writeAction(action: () -> R): ReadResult<R> = WriteAction(action)
  }
}

sealed interface ReadAndWriteScope {
  fun <R> value(value: R): ReadResult<R>
  fun <R> writeAction(action: () -> R): ReadResult<R>
}

/**
 * Runs given [action] under [read lock][com.intellij.openapi.application.Application.runReadAction]
 * **without** preventing write actions. If given [action] returns [write action][ReadAndWriteScope.writeAction]
 * as result, this write action will be run under [write lock][com.intellij.openapi.application.Application.runWriteAction]
 * if no other write actions intertwines between read action and returned write action. Read action will be re-run if a concurrent
 * write action happens after the read completion but before the returned write action was able to run.
 * In other words, it's guaranteed that no other write occurs between the read action and returned write action.
 *
 * See [constrainedReadAndWriteAction] for details.
 *
 * @see constrainedReadAction
 */
suspend fun <T> readAndWriteAction(action: ReadAndWriteScope.() -> ReadResult<T>): T {
  return constrainedReadAndWriteAction(action = action)
}

/**
 * Runs given [action] under [read lock][com.intellij.openapi.application.Application.runReadAction]
 * **without** preventing write actions. If given [action] returns [write action][ReadAndWriteScope#writeAction]
 * as result, this write action will be run under [write lock][com.intellij.openapi.application.Application.runWriteAction]
 * if no other write actions intertwines between read action and returned write action. Read action will be re-run if a concurrent
 * write action happens after the read completion but before the returned write action was able to run.
 * In other words, it's guaranteed that no other write occurs between the read action and returned write action.
 *
 * The function suspends if at the moment of calling it's not possible to acquire the read lock,
 * or if [constraints] are not [satisfied][ReadConstraint.isSatisfied].
 * If the write action happens while the [action] is running, then the [action] is canceled,
 * and the function suspends until its possible to acquire the read lock, and then the [action] is tried again.
 *
 * Since the [action] might me executed several times, it must be idempotent.
 * The function returns when given [action] was completed fully.
 * To support cancellation, the [action] must regularly invoke [com.intellij.openapi.progress.ProgressManager.checkCanceled].
 *
 * The [action] is dispatched to [Dispatchers.Default], because a read action is expected to be a CPU-bound task.
 *
 * Examples:
 * ```
 * val textAfterModification = readAndWriteAction {
 *   val psi = computePsi()
 *   writeAction {
 *     // capturing of PSI is allowed because
 *     // it's guaranteed that no other write occurs in the middle
 *     modifyPsi(psi)
 *     psi.text
 *   }
 * }
 *
 * val offset = readAndWriteAction {
 *   val psi = computePsi()
 *   if (canModify(psi)) {
 *      writeAction {
 *       modifyPsi(psi)
 *       psi.textOffset
 *     }
 *   }
 *   else {
 *     // return a value without write
 *     value(psi.textOffset)
 *   }
 * }
 * ```
 *
 */
suspend fun <T> constrainedReadAndWriteAction(vararg constraints: ReadConstraint, action: ReadAndWriteScope.() -> ReadResult<T>): T {
  return readWriteActionSupport().executeReadAndWriteAction(constraints, action = action)
}

/**
 * Runs given [action] under [write lock][com.intellij.openapi.application.Application.runWriteAction] using [Dispatchers.EDT].
 *
 * The [action] is dispatched by [Dispatchers.EDT] within the [context modality state][asContextElement].
 * If the calling coroutine is already executed by [Dispatchers.EDT], then no re-dispatch happens.
 * Acquiring the write-lock happens in blocking manner,
 * i.e. [runWriteAction][com.intellij.openapi.application.Application.runWriteAction] call will block
 * until all currently running read actions are finished.
 *
 * @see readAndWriteAction
 * @see com.intellij.openapi.command.writeCommandAction
 */
suspend fun <T> edtWriteAction(action: () -> T): T {
  return withContext(Dispatchers.EDT) {
    blockingContext {
      ApplicationManager.getApplication().runWriteAction(Computable(action))
    }
  }
}

/**
 * Runs [action] under [write lock][com.intellij.openapi.application.Application.runWriteAction].
 *
 * This function is deprecated in favor of [edtWriteAction]. This deprecation is needed to free the name [writeAction], as we are
 * planning to schedule all write actions to background by default.
 *
 * NB This function is an API stub. The implementation will change once running write actions would be allowed on other threads. This
 * function exists to make it possible to use it in suspending contexts before the platform is ready to handle write actions differently.
 */
@Experimental
suspend fun <T> writeAction(action: () -> T): T {
  return withContext(Dispatchers.EDT) {
    blockingContext {
      ApplicationManager.getApplication().runWriteAction(Computable(action))
    }
  }
}

private object RunInBackgroundWriteActionMarker
  : CoroutineContext.Element,
    CoroutineContext.Key<RunInBackgroundWriteActionMarker> {
  override val key: CoroutineContext.Key<*> get() = this
}

@Experimental
@ApiStatus.Obsolete
fun CoroutineContext.isBackgroundWriteAction(): Boolean =
  currentThreadContext()[RunInBackgroundWriteActionMarker] != null

/**
 * Runs given [action] under [write lock][com.intellij.openapi.application.Application.runWriteAction].
 *
 * This function dispatches the [action] by [Dispatchers.Default] within the [context modality state][asContextElement].
 * Acquiring the write-lock happens in blocking manner,
 * i.e. [runWriteAction][com.intellij.openapi.application.Application.runWriteAction] call will block
 * until all currently running read actions are finished.
 *
 * NB This function is an API stub.
 * The implementation will change once running write actions would be allowed on other threads.
 * This function exists to make it possible to use it in suspending contexts
 * before the platform is ready to handle write actions differently.
 *
 * @see readAndWriteAction
 * @see com.intellij.openapi.command.writeCommandAction
 */
@Experimental
@Internal
suspend fun <T> backgroundWriteAction(action: () -> T): T {
  val context = if (useBackgroundWriteAction) {
    Dispatchers.Default + RunInBackgroundWriteActionMarker
  }
  else {
    Dispatchers.EDT
  }

  return withContext(context) {
    val dumpJob = if (useBackgroundWriteAction) launch {
      delay(10.seconds)
      val dump = ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false)
      val dumpDir = PathManager.getLogDir().resolve("bg-wa")
      val file = dumpDir.resolve("thread-dump-${Random.nextInt().absoluteValue}.txt")
      try {
        Files.createDirectories(dumpDir)
        Files.createFile(file)
        file.writeText(dump.rawDump)
        logger<ApplicationManager>().warn(
          """Cannot execute background write action in 10 seconds. Thread dump is stored in ${file.toUri()}""")
      }
      catch (_: IOException) {
        logger<ApplicationManager>().warn(
          """Cannot execute background write action in 10 seconds.
Thread dump:
${dump.rawDump}""")
      }

    }
    else null
    try {
      @Suppress("ForbiddenInSuspectContextMethod")
      ApplicationManager.getApplication().runWriteAction(ThrowableComputable(action))
    }
    finally {
      dumpJob?.cancel()
    }
  }
}

/**
 * Runs given [action] under [write intent read lock][com.intellij.openapi.application.Application.runWriteIntentReadAction].
 *
 * Acquiring the write intent lock happens in blocking manner,
 * i.e. [runWriteIntentReadAction][com.intellij.openapi.application.Application.runWriteIntentReadAction] call will block
 * until all currently running write actions are finished.
 *
 * NB This function is an API stub.
 * The implementation will change once running write actions would be allowed on other threads.
 * This function exists to make it possible to use it in suspending contexts
 * before the platform is ready to handle write actions differently.
 *
 * @see readAndWriteAction
 * @see com.intellij.openapi.command.writeCommandAction
 */
@Experimental
suspend fun <T> writeIntentReadAction(action: () -> T): T {
  return blockingContext {
    ApplicationManager.getApplication().runWriteIntentReadAction(ThrowableComputable(action))
  }
}

private fun readWriteActionSupport() = ApplicationManager.getApplication().getService(ReadWriteActionSupport::class.java)

@Deprecated("Moved to modality.kt", level = DeprecationLevel.HIDDEN)
fun ModalityState.asContextElement(): CoroutineContext = asContextElement()

/**
 * UI dispatcher which dispatches onto Swing event dispatching thread within the [context modality state][asContextElement].
 * The computations scheduled by this dispatcher **are** protected by the Write-Intent lock, and they are allowed to upgrade to write actions.
 *
 * If no context modality state is specified, then the coroutine is dispatched within [ModalityState.nonModal] modality state.
 *
 * This dispatcher is also installed as [Dispatchers.Main]. Prefer [Dispatchers.UI] for computations on EDT.
 */
@Suppress("UnusedReceiverParameter")
val Dispatchers.EDT: CoroutineContext get() = coroutineSupport().uiDispatcher(UiDispatcherKind.LEGACY, false)

/**
 * UI dispatcher which dispatches onto Swing event dispatching thread within the [context modality state][asContextElement].
 * If no context modality state is specified, then the coroutine is dispatched within [ModalityState.nonModal] modality state.
 *
 * The behavior of the Write-Intent lock can be configured by the use of [kind].
 *
 * Prefer [Dispatchers.UI] for computations on EDT.
 */
@Experimental
@Suppress("UnusedReceiverParameter")
@JvmOverloads
fun Dispatchers.ui(kind: UiDispatcherKind = UiDispatcherKind.STRICT, immediate: Boolean = false): CoroutineContext = coroutineSupport().uiDispatcher(kind, immediate)

/**
 * Defines the behavior of a dispatcher that manages Event Dispatch Thread.
 * The behavior of the dispatchers differs in their treatment of the Read-Write lock
 * and possibility of interaction with the IntelliJ Platform model (PSI, VFS, etc.)
 * By default, consider using [RELAX].
 */
@Experimental
enum class UiDispatcherKind {
  /**
   * This UI dispatcher **forbids** any attempt to access the RW lock.
   * Use it if you are performing strictly UI-related computations.
   */
  STRICT,

  /**
   * This UI dispatcher **allows** taking the RW lock, but **does not** acquire it by default.
   * Use it for incremental migration from [LEGACY].
   */
  RELAX,

  /**
   * This UI dispatcher **acquires** the Write-Intent lock for all computations by default.
   * We would like to move away from unconditional acquisition of the Read-Write lock, so please use [RELAX] for replacement.
   */
  @ApiStatus.Obsolete
  LEGACY;
}


@Suppress("UnusedReceiverParameter")
@get:Experimental
val Dispatchers.EdtImmediate: CoroutineContext get() = coroutineSupport().uiDispatcher(kind = UiDispatcherKind.LEGACY, immediate = true)

@Suppress("UnusedReceiverParameter")
@get:Experimental
val Dispatchers.UiImmediate: CoroutineContext get() = coroutineSupport().uiDispatcher(kind = UiDispatcherKind.STRICT, immediate = true)

/**
 * UI dispatcher which dispatches onto Swing event dispatching thread within the [context modality state][asContextElement].
 * The computations scheduled by this dispatcher are **not** protected by any lock, and it is forbidden to initiate Read or Write actions.
 *
 * If no context modality state is specified, then the coroutine is dispatched within [ModalityState.nonModal] modality state.
 *
 * Use [Dispatchers.UI] when in doubt, use [Dispatchers.Main] if the coroutine doesn't care about IntelliJ Platform model (PSI, VFS, etc.),
 * e.g., when it can be executed outside of IJ process.
 */
@get:Experimental
@Suppress("UnusedReceiverParameter")
val Dispatchers.UI: CoroutineContext get() = coroutineSupport().uiDispatcher(kind = UiDispatcherKind.STRICT, immediate = false)

private fun coroutineSupport() = ApplicationManager.getApplication().getService(CoroutineSupport::class.java)
