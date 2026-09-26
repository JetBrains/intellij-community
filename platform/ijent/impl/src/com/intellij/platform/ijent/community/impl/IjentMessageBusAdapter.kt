// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.ide.ApplicationActivity
import com.intellij.internal.statistic.SmartModeTransitionPhase
import com.intellij.internal.statistic.SmartModeTransitionPhaseListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.CONNECT_FINISHED
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.CONNECT_STARTED
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.DEPLOY_FINISHED
import com.intellij.platform.ijent.spi.IjentDeployingStrategy.DeployEvent.DEPLOY_STARTED

internal class IjentMessageBusAdapter : ApplicationActivity {
  override suspend fun execute() {
    IjentDeployingStrategy.deployEvents.collect { event ->
      val phase = when (event) {
        DEPLOY_STARTED, DEPLOY_FINISHED -> SmartModeTransitionPhase.EEL_DEPLOY
        CONNECT_STARTED, CONNECT_FINISHED -> SmartModeTransitionPhase.EEL_CONNECT
      }

      val publisher = ApplicationManager.getApplication()?.messageBus?.syncPublisher(SmartModeTransitionPhaseListener.TOPIC)
      when (event) {
        DEPLOY_STARTED, CONNECT_STARTED -> {
          publisher?.phaseStarted(phase)
        }
        DEPLOY_FINISHED, CONNECT_FINISHED -> {
          publisher?.phaseFinished(phase)
        }
      }
    }
  }
}
