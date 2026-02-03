// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.locking.impl.listeners

import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Provides the possibility to react to the acquisition and release of the RWI lock.
 * This is a very low-level functionality.
 * The implementers are expected to manage states on their own.
 */
@ApiStatus.Internal
interface LockAcquisitionListener<T> : EventListener {
  fun beforeWriteLockAcquired(): T
  fun afterWriteLockAcquired(beforeResult: T)
}