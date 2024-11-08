// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.platform.instanceContainer.InstanceContainer
import kotlinx.coroutines.CancellationException

sealed interface InstanceHolder {

  fun instanceClassName(): String

  /**
   * May trigger class loading.
   * @return class of the instance, even if the instance was not initialized, or the initialization failed with error
   * @throws Throwable any error occurred during class loading
   */
  fun instanceClass(): Class<*>

  /**
   * @return the initialized instance, or `null` if the initialization was not started, or not yet completed
   * @throws Throwable if initialization completed with an error
   */
  fun tryGetInstance(): Any?

  /**
   * @see InstanceContainer.requestedInstance
   */
  suspend fun getInstanceIfRequested(): Any?

  /**
   * Suspends, if the instance initialization was started in another coroutine.
   * If the initialization was not started, starts the initialization and suspends until it's completed.
   *
   * @return the initialized instance
   * @throws Throwable any error occurred during initialization
   * @throws CancellationException if the calling coroutine was canceled while suspended
   */
  suspend fun getInstance(keyClass: Class<*>?): Any

  suspend fun getInstanceInCallerContext(keyClass: Class<*>?): Any
}
