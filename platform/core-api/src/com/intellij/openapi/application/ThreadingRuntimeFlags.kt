// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

/**
 * - `false` means that [backgroundWriteAction] will perform write actions from a non-modal context on a background thread
 * - `true` means that [backgroundWriteAction] will perform write actions in and old way (on EDT)
 */
@ApiStatus.Internal
val useBackgroundWriteAction: Boolean = System.getProperty("idea.background.write.action.enabled", "true").toBoolean()

/**
 * - `true` means some high-level Swing code will use write-intent lock defensively for execution of user's code
 * - `false` means that write-intent lock will not be inserted there
 *
 * See IJPL-199557
 */
@ApiStatus.Internal
val wrapHighLevelFunctionsInWriteIntent: Boolean = System.getProperty("idea.wrap.high.level.functions.in.write.intent", "false").toBoolean()

/**
 * - `true` means [com.intellij.openapi.command.CommandProcessor] will use write-intent lock for execution commands
 * - `false` means that write-intent lock will not be inserted there
 *
 * See IJPL-215129
 */
@ApiStatus.Internal
val wrapCommandsInWriteIntent: Boolean = System.getProperty("idea.wrap.commands.in.write.intent", "false").toBoolean()

/**
 * - `true` means some high-level Swing code will use write-intent lock defensively for execution of input events
 * - `false` means that write-intent lock will not be inserted there
 */
@ApiStatus.Internal
val wrapHighLevelInputEventsInWriteIntentLock: Boolean = System.getProperty("idea.wrap.high.level.input.events.in.write.intent", "false").toBoolean()


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
