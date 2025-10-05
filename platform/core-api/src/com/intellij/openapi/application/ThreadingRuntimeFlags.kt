// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

/**
 * - `false` means that [backgroundWriteAction] will perform write actions from a non-modal context on a background thread
 * - `true` means that [backgroundWriteAction] will perform write actions in and old way (on EDT)
 */
@ApiStatus.Internal
val useBackgroundWriteAction: Boolean = System.getProperty("idea.background.write.action.enabled", "true").toBoolean()

/**
 * - `false` means some high-level Swing code will not use write-intent lock defensively for execution of user's code
 * - `true` means that write-intent lock will be inserted in more places
 *
 * See IJPL-199557
 */
@ApiStatus.Internal
val doNotWrapHighLevelActionsInWriteIntent: Boolean = System.getProperty("idea.do.not.wrap.high.level.functions.in.write.intent", "false").toBoolean()

/**
 * - `false` means that [backgroundWriteAction] will block the thread during lock acquisition
 * - `true` means that [backgroundWriteAction] will suspend during lock acquisition
 */
@ApiStatus.Internal
val useTrueSuspensionForWriteAction: Boolean = System.getProperty("idea.true.suspension.for.write.action", "true").toBoolean()

/**
 * - `false` means wrong action chains are ignored and not reported
 * - `true` means chains of actions like `WriteIntentReadAction -> ReadAction -> WriteAction` will be reported as warnings
 *
 *  Such reporting fails tests as I/O takes too much time and tests timeouts.
 */
@get:ApiStatus.Internal
val reportInvalidActionChains: Boolean = System.getProperty("ijpl.report.invalid.action.chains", "false").toBoolean()

@get:ApiStatus.Internal
val installSuvorovProgress: Boolean = System.getProperty("ide.install.suvorov.progress", "true").toBoolean()

@get:ApiStatus.Internal
val useDebouncedDrawingInSuvorovProgress: Boolean = System.getProperty("ide.suvorov.progress.debounced.drawing", "true").toBoolean()

/**
 * - `true` means that [kotlinx.coroutines.Dispatchers.EDT] will acquire write-intent lock in non-blocking way
 * - `false` means that [kotlinx.coroutines.Dispatchers.EDT] will block on the acquisition of high-level write-intent lock.
 */
@get:ApiStatus.Internal
val useNonBlockingIntentLockForEdtCoroutines: Boolean = System.getProperty("ide.non.blocking.write.intent.lock.for.edt.coroutines", "true").toBoolean()

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
