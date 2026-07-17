// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.spi.IjentConnectionContext
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.Companion.deployEvents
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.CONNECT_FINISHED
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.CONNECT_STARTED
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.DEPLOY_FINISHED
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.DEPLOY_STARTED
import com.intellij.platform.ijent.spi.IjentSessionProvider

/**
 * Strategy for deploying IJent over TCP when standard process spawning is not available
 * (e.g., SSH connections where IJent must be uploaded and executed remotely).
 *
 * Implementations handle binary deployment, process lifecycle, and port forwarding.
 *
 * Deploy and connect events are published to [IjentDeployingStrategy.deployEvents] — used to publish FUS events without pulling
 * `intellij.platform.statistics` into ijent.
 */
abstract class IjentIsolatedTcpDeployingStrategy : IjentDeployingStrategy {
  /**
   * Deploys and launches IJent in the target environment.
   * @return [IjentConnectionContext] with the mediator and deployment metadata
   */
  protected abstract suspend fun deploy(): IjentConnectionContext

  final override suspend fun createIjentSession(provider: IjentSessionProvider): IjentSession {
    deployEvents.emit(DEPLOY_STARTED)
    val ctx = deploy()
    deployEvents.emit(DEPLOY_FINISHED)

    deployEvents.emit(CONNECT_STARTED)
    return provider.connect(ctx).also {
      deployEvents.emit(CONNECT_FINISHED)
    }
  }
}