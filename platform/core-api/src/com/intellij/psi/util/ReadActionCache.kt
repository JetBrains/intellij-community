// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus

/**
 * An application service, which provides a [processingContext] bound to the current read-action.
 * It allows storing some data, which is actual for the current read-action only.
 * The [processingContext] is shared only in the current thread, thus no thread-contention is expected,
 * and no synchronisation is needed.
 * After the current read-action ends, the [processingContext] is removed and all the data inside it becomes
 * eligible for garbage collection. It makes a good way for a [short-living caching][ReadActionCachedValue].
 *
 * The [processingContext] is available only inside read-actions, which guarantee that "world" will not change
 * between accessing the same [processingContext].
 * It also available in write-action when called inside the [allowInWriteAction] blocks.
 *
 * @see ReadActionCachedValue
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface ReadActionCache {

  /**
   * @return the [ProcessingContext] associated with the current read action,
   * or `null` if called outside the read-action.
   * Inside write action returns `null` unless called inside [allowInWriteAction].
   */
  val processingContext: ProcessingContext?

  /**
   * Allows using the [processingContext] inside the passed `supplier` even in write action.
   * It implies that inside this `supplier` no real modification is made, and it could be considered as read-block.
   */
  fun <T> allowInWriteAction(supplier: () -> T): T  
  
  /**
   * @see allowInWriteAction
   */
  fun allowInWriteAction(runnable: Runnable)


  companion object {
    @Suppress("IncorrectServiceRetrieving") // registered as "fake service" in ApplicationImpl
    @JvmStatic
    fun getInstance(): ReadActionCache = ApplicationManager.getApplication().getService(ReadActionCache::class.java)
  }
}