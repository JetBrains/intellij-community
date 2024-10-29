// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.*
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Unmodifiable
import java.util.*
import javax.swing.JComponent

/**
 * A service managing the IDE's 'dumb' mode: when indexes are updated in the background, and the functionality is very much limited.
 * Only the explicitly allowed functionality is available. Usually, it's allowed by implementing [DumbAware] interface.
 *
 *
 * "Dumb" mode starts and ends in a [com.intellij.openapi.application.WriteAction], so if you're inside a [ReadAction]
 * on a background thread, it won't suddenly begin in the middle of your operation. But note that whenever you start
 * a top-level read action on a background thread, you should be prepared to anything being changed, including "dumb"
 * mode being suddenly on and off. To avoid executing a read action in "dumb" mode, please use [runReadActionInSmartMode] or
 * [com.intellij.openapi.application.NonBlockingReadAction.inSmartMode].
 *
 *
 * More information about dumb mode could be found here: [IndexNotReadyException]
 */
abstract class DumbService {
  /**
   * The tracker is advanced each time we enter/exit from dumb mode.
   */
  abstract val modificationTracker: ModificationTracker

  /**
   * To avoid race conditions, use it only in EDT thread or inside read-action. See documentation for this class [DumbService]
   *
   * @return whether the IDE is in dumb mode, which means that right now indexes are updated in the background.
   * The IDE offers only limited functionality at such times, e.g., plain text file editing and version control operations.
   */
  abstract val isDumb: Boolean

  /**
   * Executes the runnable as soon as possible on AWT Event Dispatch when:
   *  * project is initialized
   *  * and there's no dumb mode in progress
   *
   * This may also happen immediately if these conditions are already met.
   *
   * Note that it's not guaranteed that the dumb mode won't start again during this runnable execution, it should manage that situation explicitly.
   */
  abstract fun runWhenSmart(runnable: Runnable)

  /**
   * Pause the current thread until dumb mode ends and then continue execution.
   * NOTE: there are no guarantees that a new dumb mode won't begin before the next statement.
   * Hence: use with care. Consider using [runWhenSmart] or [runReadActionInSmartMode] instead
   *
   * See [Project.waitForSmartMode] for using in a suspend context.
   */
  @RequiresBlockingContext
  abstract fun waitForSmartMode()

  /**
   * DEPRECATED.
   *
   * Use instead:
   * - [com.intellij.openapi.application.smartReadAction]
   * - [com.intellij.openapi.application.NonBlockingReadAction] with `inSmartMode` option.
   *
   * WARNING: This method does not have any effect if it is called inside another read action.
   *
   * Otherwise, it pauses the current thread until dumb mode ends, and then runs the read action.
   * In this case indexes are guaranteed to be available inside
   *
   * @throws ProcessCanceledException if the project is closed during dumb mode
   */
  @Deprecated("""
    This method is dangerous because it does not provide any guaranties if it is called inside another read action.
    Instead, consider using  
    - `com.intellij.openapi.application.smartReadAction` 
    - `NonBlockingReadAction(...).inSmartMode()` 
  """)
  fun <T> runReadActionInSmartMode(r: Computable<T>): T {
    val result = Ref<T>()
    runReadActionInSmartMode { result.set(r.compute()) }
    return result.get()
  }

  /**
   * Backward compatibility for plugins, use [tryRunReadActionInSmartMode] with [DumbModeBlockedFunctionality] instead
   */
  @Obsolete
  fun <T> tryRunReadActionInSmartMode(task: Computable<T>,
                                      notification: @NlsContexts.PopupContent String?): T? {
    return tryRunReadActionInSmartMode(task, notification, DumbModeBlockedFunctionality.Other)
  }

  /**
   * WARNING: this method does not guarantee that Indexes are available if called under read action.
   *
   * Consider using [com.intellij.openapi.application.smartReadAction] or
   * [com.intellij.openapi.application.NonBlockingReadAction] with `inSmartMode` option.
   */
  fun <T> tryRunReadActionInSmartMode(task: Computable<T>,
                                      notification: @NlsContexts.PopupContent String?,
                                      functionality: DumbModeBlockedFunctionality): T? {
    return if (ApplicationManager.getApplication().isReadAccessAllowed) {
      try {
        task.compute()
      }
      catch (e: IndexNotReadyException) {
        notification?.let { showDumbModeNotificationForFunctionality(it, functionality) }
        null
      }
    }
    else {
      runReadActionInSmartMode(task)
    }
  }

  /**
   * DEPRECATED.
   *
   * Use instead:
   * - [com.intellij.openapi.application.smartReadAction]
   * - [com.intellij.openapi.application.NonBlockingReadAction] with `inSmartMode` option.
   *
   * WARNING: This method does not have any effect if it is called inside another read action.
   *
   * Otherwise, it pauses the current thread until dumb mode ends, and then runs the read action.
   * In this case indexes are guaranteed to be available inside
   *
   * @throws ProcessCanceledException if the project is closed during dumb mode
   */
  @Deprecated("""
    This method is dangerous because it does not provide any guaranties if it is called inside another read action.
    Instead, consider using  
    - `com.intellij.openapi.application.smartReadAction` 
    - `NonBlockingReadAction(...).inSmartMode()` 
  """)
  fun runReadActionInSmartMode(r: Runnable) {
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
      // we can't wait for smart mode to begin (it'd result in a deadlock),
      // so let's just pretend it's already smart and fail with IndexNotReadyException if not
      r.run()
      return
    }
    while (true) {
      waitForSmartMode()
      val success = ReadAction.compute<Boolean, RuntimeException> {
        if (project.isDisposed) {
          throw ProcessCanceledException()
        }
        if (isDumb) {
          return@compute false
        }
        r.run()
        true
      }
      if (success) {
        break
      }
    }
  }

  /**
   * Pause the current thread until dumb mode ends, and then attempt to execute the runnable. If it fails due to another dumb mode having started,
   * try again until the runnable can complete successfully.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("This method provides no guarantees and should be avoided, please use [#runReadActionInSmartMode] instead.")
  fun repeatUntilPassesInSmartMode(r: Runnable) {
    while (true) {
      waitForSmartMode()
      try {
        r.run()
        return
      }
      catch (ignored: IndexNotReadyException) {
      }
    }
  }

  /**
   * Invoke the runnable later on EventDispatchThread AND when IDE isn't in dumb mode.
   * The runnable won't be invoked if the project is disposed during dumb mode.
   */
  abstract fun smartInvokeLater(runnable: Runnable)

  /**
   * Invoke the runnable later on EventDispatchThread with the given modality state AND when IDE isn't in dumb mode.
   * The runnable won't be invoked if the project is disposed during dumb mode.
   */
  abstract fun smartInvokeLater(runnable: Runnable, modalityState: ModalityState)

  /**
   * @return all the elements of the given array if there's no dumb mode currently, or the dumb-aware ones if [isDumb] is `true`.
   * @see isDumbAware
   */
  fun <T> filterByDumbAwareness(array: Array<T>): List<T> {
    return filterByDumbAwareness(array.toList())
  }

  /**
   * @return all the elements of the given collection if there's no dumb mode currently, or the dumb-aware ones if [isDumb] is true.
   * @see isDumbAware
   */
  @Contract(pure = true)
  fun <T> filterByDumbAwareness(collection: Collection<T>): @Unmodifiable List<T> {
    if (isDumb) {
      val result = ArrayList<T>(collection.size)
      for (element in collection) {
        if (isDumbAware(element)) {
          result.add(element)
        }
      }
      return result
    }
    return if (collection is List<*>) collection as List<T> else collection.toList()
  }

  /**
   * Queues a task to be executed in "dumb mode", where access to indices is forbidden. Tasks are executed sequentially
   * in background unless [completeJustSubmittedTasks] is called in the same dispatch thread activity.
   *
   *
   * Tasks can specify custom "equality" policy via [DumbModeTask.tryMergeWith].
   * Calling this method has no effect if an "equal" task is already enqueued (but not yet running).
   *
   * Alternatively one may call a short-cut [DumbModeTask.queue] instead.
   */
  abstract fun queueTask(task: DumbModeTask)

  /**
   * Cancels the given task. If it's in the queue, it won't be executed. If it's already running, its
   * [com.intellij.openapi.progress.ProgressIndicator] is canceled, so the next [ProgressManager.checkCanceled] call
   * will throw [ProcessCanceledException].
   */
  abstract fun cancelTask(task: DumbModeTask)

  /**
   * Cancels all tasks and wait when their execution is finished. Should be called on write thread.
   */
  @ApiStatus.Internal
  abstract fun cancelAllTasksAndWait()

  /**
   * Runs the "just submitted" tasks under a modal dialog. "Just submitted" means that tasks were queued for execution
   * earlier within the same Swing event dispatch thread event processing, and there were no other tasks already running at that moment.
   * Otherwise, this method does nothing.
   *
   * This functionality can be useful in refactorings (invoked in "smart mode"), when after VFS or root changes
   * (which could start "dumb mode") some references need to be resolved (which again requires "smart mode").
   *
   * Should be invoked on dispatch thread.
   * It's the caller's responsibility to invoke this method only when the model is in internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc.
   */
  abstract fun completeJustSubmittedTasks()

  /**
   * Replaces given component temporarily with "Not available until indices are built" label during dumb mode.
   *
   * @return Wrapped component.
   */
  abstract fun wrapGently(dumbUnawareContent: JComponent, parentDisposable: Disposable): JComponent

  /**
   * Adds a "Results might be incomplete during indexing." decorator to a given component during dumb mode.
   *
   * @param dumbAwareContent - a component to wrap
   * @param updateRunnable - an action to execute when dumb mode state changed or user explicitly request reload panel
   *
   * @return Wrapped component.
   */
  abstract fun wrapWithSpoiler(dumbAwareContent: JComponent, updateRunnable: Runnable, parentDisposable: Disposable): JComponent

  /**
   * Disables given component temporarily during dumb mode.
   */
  fun makeDumbAware(componentToDisable: JComponent, parentDisposable: Disposable) {
    componentToDisable.isEnabled = !isDumb
    project.messageBus.connect(parentDisposable).subscribe(DUMB_MODE, object : DumbModeListener {
      override fun enteredDumbMode() {
        componentToDisable.isEnabled = false
      }

      override fun exitDumbMode() {
        componentToDisable.isEnabled = true
      }
    })
  }

  /**
   * Use [showDumbModeNotificationForAction] or [showDumbModeNotificationForFunctionality] instead
   */
  @Obsolete
  abstract fun showDumbModeNotification(message: @NlsContexts.PopupContent String)

  /**
   * Show a notification when given functionality is not available during dumb mode.
   */
  abstract fun showDumbModeNotificationForFunctionality(message: @NlsContexts.PopupContent String,
                                                        functionality: DumbModeBlockedFunctionality)

  abstract fun showDumbModeNotificationForAction(message: @NlsContexts.PopupContent String, actionId: String?)

  /**
   * Shows balloon about indexing blocking those actions until it is hidden (by key input, mouse event, etc.) or indexing stops.
   * @param runWhenSmartAndBalloonStillShowing will be executed in smart mode on EDT, balloon won't be dismissed by user's actions
   */
  abstract fun showDumbModeActionBalloon(balloonText: @NlsContexts.PopupContent String,
                                         runWhenSmartAndBalloonStillShowing: Runnable,
                                         actionIds: List<String>)

  abstract val project: Project

  /**
   * Invokes the given runnable with alternative resolve set to true if dumb mode is enabled.
   *
   * @see isAlternativeResolveEnabled
   */
  fun withAlternativeResolveEnabled(runnable: Runnable) {
    val isDumb = isDumb
    if (isDumb) isAlternativeResolveEnabled = true
    try {
      runnable.run()
    }
    finally {
      if (isDumb) isAlternativeResolveEnabled = false
    }
  }

  /**
   * Invokes the given computable with alternative resolve set to `true` if dumb mode is enabled.
   *
   * @see isAlternativeResolveEnabled
   */
  fun <T, E : Throwable?> computeWithAlternativeResolveEnabled(runnable: ThrowableComputable<T, E>): T {
    val isDumb = isDumb
    if (isDumb) isAlternativeResolveEnabled = true
    return try {
      runnable.compute()
    }
    finally {
      if (isDumb) isAlternativeResolveEnabled = false
    }
  }

  /**
   * Invokes the given runnable with alternative resolve set to `true` if dumb mode is enabled.
   *
   * @see isAlternativeResolveEnabled
   */
  fun <E : Throwable?> runWithAlternativeResolveEnabled(runnable: ThrowableRunnable<E>) {
    val isDumb = isDumb
    if (isDumb) isAlternativeResolveEnabled = true
    try {
      runnable.run()
    }
    finally {
      if (isDumb) isAlternativeResolveEnabled = false
    }
  }

  /**
   * Invokes the given runnable with alternative resolve set to `true` if dumb mode is enabled.
   *
   * @see isAlternativeResolveEnabled
   */
  inline fun <T> withAlternativeResolveEnabled(runnable: () -> T): T {
    val isDumb = isDumb
    val old = isAlternativeResolveEnabled
    if (isDumb) isAlternativeResolveEnabled = true
    try {
      return runnable()
    }
    finally {
      if (isDumb) isAlternativeResolveEnabled = old
    }
  }

  /**
   * Enables or disables alternative resolve strategies for the current thread.
   *
   * Normally reference resolution uses indexes, and hence is not available in dumb mode. In some cases, alternative ways
   * of performing resolve are available, although much slower. It's impractical to always use these ways because it'll
   * lead to overloaded CPU (especially given there's also indexing in progress). But for some explicit user actions
   * (e.g., explicit Goto Declaration) turning on these slower methods is beneficial.
   *
   * NOTE: even with alternative resolution enabled, methods like `resolve()`, `findClass()` etc. may still throw
   * [IndexNotReadyException]. So alternative resolve is not a panacea, it might help provide navigation in some cases
   * but not in all.
   *
   * A typical usage would involve `try-finally`, where the alternative resolution is first enabled, then an action is performed,
   * and then alternative resolution is turned off in the `finally` block.
   */
  @set:Deprecated(
    """Use [#runWithAlternativeResolveEnabled(ThrowableRunnable)] or [#computeWithAlternativeResolveEnabled(ThrowableComputable)]
    or [#withAlternativeResolveEnabled(Runnable)] instead""")
  abstract var isAlternativeResolveEnabled: Boolean

  /**
   * Runs a heavy activity and pauses indexing (if any) for this time. The user still can manually pause and resume the indexing.
   * In that case, indexing won't be resumed automatically after the activity finishes.
   *
   * @param activityName the text (a noun phrase) to display as a reason for the indexing being paused
   */
  abstract fun suspendIndexingAndRun(activityName: @NlsContexts.ProgressText String, activity: Runnable)

  /**
   * Runs a heavy activity and pauses indexing (if any) for this time. The user still can manually pause and resume the indexing.
   * In that case, indexing won't be resumed automatically after the activity finishes.
   *
   * @param activityName the text (a noun phrase) to display as a reason for the indexing being paused
   */
  @Experimental
  abstract suspend fun suspendIndexingAndRun(activityName: @NlsContexts.ProgressText String, activity: suspend () -> Unit)

  @ApiStatus.Internal
  abstract fun runWithWaitForSmartModeDisabled(): AccessToken

  /**
   * @see [DUMB_MODE]
   */
  interface DumbModeListener {
    /**
     * The event arrives on EDT.
     */
    fun enteredDumbMode() {}

    /**
     * The event arrives on EDT.
     */
    fun exitDumbMode() {}
  }

  @ApiStatus.Internal
  abstract fun unsafeRunWhenSmart(runnable: Runnable)

  /**
   * return true if [thing] can be used in current dumb context, i.e., either the [thing] is [isDumbAware] or the current context is smart; return false otherwise
   */
  fun isUsableInCurrentContext(thing: Any) : Boolean {
    return !isDumb || isDumbAware(thing)
  }

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val DUMB_MODE: Topic<DumbModeListener> = Topic("dumb mode", DumbModeListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun isDumb(project: Project): Boolean {
      return getInstance(project).isDumb
    }

    @JvmStatic
    fun <T: Any> getDumbAwareExtensions(project: Project, extensionPoint: ExtensionPointName<T>): List<T> {
      val point = extensionPoint.point
      val size = point.size()
      if (size == 0) {
        return Collections.emptyList()
      }

      if (!getInstance(project).isDumb) {
        return point.extensionList
      }

      val result = ArrayList<T>(size)
      for (item in extensionPoint.filterableLazySequence()) {
        val aClass = item.implementationClass ?: continue
        if (DumbAware::class.java.isAssignableFrom(aClass)) {
          result.add(item.instance ?: continue)
        }
        else if (PossiblyDumbAware::class.java.isAssignableFrom(aClass)) {
          val instance = item.instance ?: continue
          if ((instance as PossiblyDumbAware).isDumbAware) {
            result.add(instance)
          }
        }
      }
      return result
    }

    @JvmStatic
    fun <T: Any> getDumbAwareExtensions(project: Project, extensionPoint: ProjectExtensionPointName<T>): @Unmodifiable List<T> {
      return getInstance(project).filterByDumbAwareness(extensionPoint.getExtensions(project))
    }

    @JvmStatic
    fun getInstance(project: Project): DumbService = project.service()

    @Suppress("SSBasedInspection")
    @JvmStatic
    @Contract("null -> false", pure = true)
    fun isDumbAware(o: Any?): Boolean {
      return if (o is PossiblyDumbAware) o.isDumbAware else o is DumbAware
    }

    /**
     * @see completeJustSubmittedTasks
     */
    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Obsolete, does nothing, just executes the passed runnable.", ReplaceWith("runnable.run()"))
    fun allowStartingDumbModeInside(permission: DumbModePermission, runnable: Runnable) {
      runnable.run()
    }
  }
}
