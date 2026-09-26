// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.IjentUnavailableException
import kotlinx.coroutines.CompletableDeferred

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
  val ijentProcessScope: IjentScope
  val processExit: SafeDeferred<Unit>
}

/**
 * Context for establishing IJent connection, containing the session mediator and deployment metadata.
 */
class IjentConnectionContext(
  val mediator: IjentSessionMediator,
  val targetPlatform: EelPlatform,
  val connectionStrategy: IjentConnectionStrategy,
  val parentPidToWatch: Long? = null,
  /**
   * Makes closing the session ask IJent to terminate itself in-band, while the gRPC channel is still open. Set it
   * when this session owns the remote process's lifetime and the binary runs with `--no-shutdown-on-disconnect`
   * (any other binary exits on disconnect by itself). Deployers that end the process out-of-band — rd-eel and the
   * gateway run the binary with the same argv flag — deliberately leave this `false`.
   */
  val noShutdownOnDisconnect: Boolean = false,
  /**
   * Completed once closing the session is past the point of asking IJent to terminate in-band: the request was
   * answered, given up on, or never needed. A teardown that would sever the transport carrying that request must
   * await this first.
   */
  val remoteTerminationSettled: CompletableDeferred<Unit> = CompletableDeferred(),
)