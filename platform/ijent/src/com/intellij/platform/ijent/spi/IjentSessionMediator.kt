// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.ijent.IjentUnavailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

/**
 * A wrapper for the Ijent process. The wrapper logs stderr lines, waits for the exit code, terminates the process in case
 * of problems in the IDE.
 *
 * [processExit] never throws. When it completes, it either means that the process has finished, or that the whole scope of IJent processes
 * is canceled.
 *
 * [ijentProcessScope] should be used by the [com.intellij.platform.ijent.IjentApi] implementation for launching internal coroutines.
 * No matter if IJent exits expectedly or not, an attempt to do anything with [ijentProcessScope] after the IJent has exited
 * throws [IjentUnavailableException].
 */
sealed interface IjentSessionMediator {
  val ijentProcessScope: CoroutineScope
  val processExit: Deferred<Unit>
}

/**
 * Context for establishing IJent connection, containing the session mediator and deployment metadata.
 */
class IjentConnectionContext(
  val mediator: IjentSessionMediator,
  val targetPlatform: EelPlatform,
  val remoteBinaryPath: String,
  val connectionStrategy: IjentConnectionStrategy,
)