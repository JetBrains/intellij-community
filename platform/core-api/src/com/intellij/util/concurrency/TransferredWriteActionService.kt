// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TransferredWriteActionService {

  /**
   * Executes [action] on EDT under write action.
   * This function is semantically equivalent to [javax.swing.SwingUtilities.invokeAndWait]
   *
   * The difference is that raw `invokeAndWait` is prone to deadlocks:
   * ```kotlin
   * // background thread
   * writeAction {
   *   invokeAndWait(...)
   * }
   *
   * // edt
   * writeIntentReadAction(...)
   * ```
   * Here `invokeAndWait` cannot start because EDT is blocked on acquisition of write-intent lock,
   * which in turn cannot be acquired because a background thread is holding write action.
   * This function is able to overcome this limitation.
   *
   * It is used for invocation of EDT-dependent code synchronously from a background write action.
   * Its use should be limited to background write action only.
   */
  @RequiresWriteLock
  @RequiresBackgroundThread
  fun runOnEdtWithTransferredWriteActionAndWait(action: Runnable)
}