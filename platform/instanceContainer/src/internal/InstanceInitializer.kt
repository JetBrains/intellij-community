// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * This interface is supposed to be implemented by whoever does the registration.
 */
@OverrideOnly
interface InstanceInitializer {
  val instanceClassName: String

  val overridable: Boolean

  fun loadInstanceClass(keyClass: Class<*>?): Class<*>

  suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any

  /**
   * Does the slow work that [createInstance] needs, for example, file IO, before the initialization starts.
   * It runs in the thread of the caller, outside the non-cancellable initialization coroutine.
   * Thus, other callers do not wait for this work, and the work can be canceled.
   *
   * It can run more than once, and in more than one thread at the same time.
   * It must not create the instance.
   */
  fun prepare(instanceClass: Class<*>) {
  }
}
