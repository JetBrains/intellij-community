// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

/**
 * - `false` means that lock permits are bound only to threads
 * - `true` means that lock permits also stored in coroutine contexts
 */
@get:ApiStatus.Internal
val isLockStoredInContext: Boolean = System.getProperty("ide.store.lock.in.context", "true").toBoolean()

/**
 * - `true` means that the IDE will use n-locks backend for read, write, and write-intent actions
 * - `false` means that the IDE will use backend with two locks
 */
@get:ApiStatus.Internal
val useNestedLocking: Boolean = System.getProperty("ide.nested.locking.enabled", "true").toBoolean()

/**
 * - `false` means that [backgroundWriteAction] will perform write actions from a non-modal context on a background thread
 * - `true` means that [backgroundWriteAction] will perform write actions in and old way (on EDT)
 */
@ApiStatus.Internal
val useBackgroundWriteAction: Boolean = useNestedLocking && System.getProperty("idea.background.write.action.enabled", "false").toBoolean()

/**
 * - `false` means wrong action chains are ignored and not reported
 * - `true` means chains of actions like `WriteIntentReadAction -> ReadAction -> WriteAction` will be reported as warnings
 *
 *  Such reporting fails tests as I/O takes too much time and tests timeouts.
 */
@get:ApiStatus.Internal
val reportInvalidActionChains: Boolean = System.getProperty("ijpl.report.invalid.action.chains", "false").toBoolean()

/**
 * Represents the deadline before blocking read lock acquisition starts compensating parallelism for coroutine worker threads
 */
@get:ApiStatus.Internal
val readLockCompensationTimeout: Int = try {
  System.getProperty("ide.read.lock.compensation.timeout.ms", "250").toInt()
}
catch (_: NumberFormatException) {
  -1
}
