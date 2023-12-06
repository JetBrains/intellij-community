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

  fun loadInstanceClass(keyClass: Class<*>?): Class<*>

  suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any
}
