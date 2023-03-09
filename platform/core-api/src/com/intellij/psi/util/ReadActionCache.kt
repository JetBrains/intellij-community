// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ReadActionCache {

  /**
   * @return the [ProcessingContext] associated with the current read action,
   * or `null` if called outside the read-action.
   * Inside write action returns `null` unless called inside [allowInWriteAction].
   * 
   * @see [ReadActionCachedValue]
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
    @JvmStatic
    fun getInstance(): ReadActionCache = ApplicationManager.getApplication().getService(ReadActionCache::class.java)
  }
}