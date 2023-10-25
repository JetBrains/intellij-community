// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import java.lang.Deprecated

interface ThreadingSupport {
  /**
   * Runs the specified computation in a write intent. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * See also [WriteIntentReadAction.compute] for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   */
  // @Throws(E::class)
  fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T


  /**
   * Acquires Write Intent lock if it's not acquired by the current thread.
   *
   * This is low-level API, please use [WriteIntentReadAction].
   *
   * @param invokedClassFqn fully qualified name of the class requiring the write-intent lock.
   * @return `true` if this call acquired lock, `false` if lock was taken already.
   */
  @ApiStatus.Internal
  fun acquireWriteIntentLock(invokedClassFqn: String?): Boolean

  /**
   * Release Write Intent lock acquired with [acquireWriteIntentLock].
   *
   * This is low-level API, please use [WriteIntentReadAction].
   */
  @ApiStatus.Internal
  fun releaseWriteIntentLock()

  /**
   * Checks, is Write Intent lock  acquired by the current thread.
   *
   * This is low-level API, please use [WriteIntentReadAction].
   *
   * @return `true` if lock is acquired, `false` otherwise.
   */
  @ApiStatus.Internal
  fun isWriteIntentLocked(): Boolean

  /**
   * Runs specified action with disabled implicit read lock if this feature is enabled with system property.
   *
   * @see com.intellij.idea.StartupUtil.isImplicitReadOnEDTDisabled
   * @param runnable action to run with disabled implicit read lock.
   */
  @ApiStatus.Internal
  fun runWithoutImplicitRead(runnable: Runnable)

  /**
   * Runs specified action with enabled implicit read lock if this feature is enabled with system property.
   *
   * @see com.intellij.idea.StartupUtil.isImplicitReadOnEDTDisabled
   * @param runnable action to run with enabled implicit read lock.
   */
  @ApiStatus.Internal
  fun runWithImplicitRead(runnable: Runnable)

  /**
   * Adds a [ReadActionListener].
   *
   * Please, use [addReadActionListener] with [Disposable] second argument.
   *
   * @param listener the listener to add
   */
  @Deprecated
  fun addReadActionListener(listener: ReadActionListener)

  /**
   * Adds a [ReadActionListener].
   *
   * @param listener the listener to add
   * @param parent   the parent disposable, whose disposal will trigger this listener's removal
   */
  fun addReadActionListener(listener: ReadActionListener, parent: Disposable)

  /**
   * Removes a [ReadActionListener].
   *
   * Please, use [addReadActionListener] with [Disposable] second argument and [Disposable.dispose].
   *
   * @param listener the listener to remove
   */
  @Deprecated
  fun removeReadActionListener(listener: ReadActionListener)

  /**
   * Runs the specified read action. Can be called from any thread. The action is executed immediately
   * if no write action is currently running, or blocked until the currently running write action completes.
   *
   * See also [ReadAction.run] for a more lambda-friendly version.
   *
   * @param action the action to run.
   * @see CoroutinesKt.readAction
   *
   * @see CoroutinesKt.readActionBlocking
   */
  @RequiresBlockingContext
  fun runReadAction(action: Runnable)

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * See also [ReadAction.compute] for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @see CoroutinesKt.readAction
   * @see CoroutinesKt.readActionBlocking
   */
  @RequiresBlockingContext
  fun <T> runReadAction(computation: Computable<T>): T

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * See also [ReadAction.compute] for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   * @see CoroutinesKt.readAction
   * @see CoroutinesKt.readActionBlocking
   */
  @RequiresBlockingContext
  // @Throws(E::class)
  fun <T, E : Throwable?> runReadAction(computation: ThrowableComputable<T, E>): T
}