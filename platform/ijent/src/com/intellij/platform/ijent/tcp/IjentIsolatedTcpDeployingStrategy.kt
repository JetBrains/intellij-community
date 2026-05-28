// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.spi.IjentConnectionContext
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentSessionProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Strategy for deploying IJent over TCP when standard process spawning is not available
 * (e.g., SSH connections where IJent must be uploaded and executed remotely).
 *
 * Implementations handle binary deployment, process lifecycle, and port forwarding.
 *
 * Subclasses may override [phaseStarted] / [phaseFinished] to observe deploy / connect
 * boundaries — used by `eel-tcp` to publish FUS events without pulling
 * `intellij.platform.statistics` into ijent.
 */
abstract class IjentIsolatedTcpDeployingStrategy : IjentDeployingStrategy {
  @ApiStatus.Internal
  enum class Phase { DEPLOY, CONNECT }

  /**
   * Deploys and launches IJent in the target environment.
   * @return [IjentConnectionContext] with the mediator and deployment metadata
   */
  protected abstract suspend fun deploy(): IjentConnectionContext

  protected open suspend fun phaseStarted(phase: Phase) {}
  protected open suspend fun phaseFinished(phase: Phase) {}

  final override suspend fun createIjentSession(provider: IjentSessionProvider): IjentSession {
    phaseStarted(Phase.DEPLOY)
    val ctx = deploy()
    phaseFinished(Phase.DEPLOY)

    phaseStarted(Phase.CONNECT)
    return provider.connect(ctx).also {
      phaseFinished(Phase.CONNECT)
    }
  }
}