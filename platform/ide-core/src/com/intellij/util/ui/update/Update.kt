// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.Cancellation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Describes a task for [MergingUpdateQueue]. Equal tasks (instances with the equal `identity` objects) are merged, i.e.,
 * only the first of them is executed. If some tasks are more generic than others override [.canEat] method.
 *
 * @see MergingUpdateQueue
 */
abstract class Update : ComparableObject.Impl, Runnable {
  private val _executeInWriteAction: Boolean

  internal open val executeInWriteAction: Boolean
    get() = _executeInWriteAction

  val priority: Int

  constructor(identity: @NonNls Any?, priority: Int) : this(identity = identity, executeInWriteAction = false, priority = priority)

  @JvmOverloads
  constructor(identity: @NonNls Any?, executeInWriteAction: Boolean = false, priority: Int = LOW_PRIORITY) : super(identity) {
    _executeInWriteAction = executeInWriteAction
    this.priority = priority
  }

  internal constructor(delegate: Update) : super() {
    _executeInWriteAction = delegate.executeInWriteAction
    priority = delegate.priority
  }

  companion object {
    const val LOW_PRIORITY: Int = 999
    const val HIGH_PRIORITY: Int = 10

    @JvmStatic
    fun create(identity: @NonNls Any?, runnable: Runnable): Update {
      return object : Update(identity) {
        override fun run() {
          runnable.run()
        }
      }
    }
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  open suspend fun execute() {
    if (executeInWriteAction) {
      @Suppress("ForbiddenInSuspectContextMethod", "RedundantSuppression")
      ApplicationManager.getApplication().runWriteAction(this)
    }
    else {
      // todo fix clients
      Cancellation.withNonCancelableSection().use {
        run()
      }
    }
  }

  open val isDisposed: Boolean
    get() = false

  open val isExpired: Boolean
    get() = false

  @Volatile
  private var isProcessed = false

  open fun wasProcessed(): Boolean = isProcessed

  open fun setProcessed() {
    isProcessed = true
  }

  /**
   * Override this method and return `true` if this task is more generic than the passed `update`.
   * For example, if this task repaints the whole frame and the passed task repaints some component on the frame,
   * the less generic tasks will be removed from the queue before execution.
   */
  open fun canEat(update: Update): Boolean = false

  @Volatile
  private var rejected = false

  open val isRejected: Boolean
    get() = rejected

  /**
   * Is called if the update was submitted to the queue but is not going to be executed because of various reasons; for example,
   * - it was eaten by another Update
   * - this update has gotten expired
   * - someone has canceled all updates
   */
  open fun setRejected() {
    rejected = true
  }

  override fun toString(): String = "${super.toString()} Objects: ${equalityObjects.joinToString()}"
}
