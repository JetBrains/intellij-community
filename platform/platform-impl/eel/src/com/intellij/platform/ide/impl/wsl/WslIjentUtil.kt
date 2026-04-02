// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentUtil")
@file:Suppress("RAW_RUN_BLOCKING")  // These functions are called by different legacy code, a ProgressIndicator is not always available.
@file:ApiStatus.Internal

package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ijent.IjentLogger.CONN_MGR_LOG
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.community.impl.guessVmIdOfWsl
import com.intellij.platform.ijent.currentCoroutineDispatcher
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun WSLDistribution.createIjentSession(
  parentScope: CoroutineScope,
  project: Project?,
  ijentLabel: String,
  wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
): IjentSession.Posix {
  val ijentSession = WslIjentDeployingStrategy(
    scope = parentScope,
    currentDispatcher = currentCoroutineDispatcher(),
    ijentLabel = ijentLabel,
    distribution = this,
    project = project,
    wslCommandLineOptionsModifier = wslCommandLineOptionsModifier
  ).createIjentSession()

  if (Registry.`is`("ijent.multiple.connections.mode")) {
    if (System.getProperty("ijent.wait.for.transport.initialization") == "true") {
      initMultipleTransports(this@createIjentSession, ijentSession)
    }
    else {
      @OptIn(DelicateCoroutinesApi::class)
      ijentSession.sessionCoroutineScope.launch { initMultipleTransports(this@createIjentSession, ijentSession) }
    }
  }

  return ijentSession
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun initMultipleTransports(wslDistribution: WSLDistribution, ijentSession: IjentSession.Posix) {
  val vmId = withContext(blockingDispatcher) { guessVmIdOfWsl() } ?: return

  // TODO Looks weird. Why do we need a descriptor for this API call?
  val descriptor = WslEelDescriptor(wslDistribution)
  val ijentApi = ijentSession.getIjentInstance(descriptor)

  if (!ijentApi.requestHyperVTransports(vmId)) {
    CONN_MGR_LOG.warn("Failed to initialize Hyper-V transports for WSL distribution ${wslDistribution.id}." +
                      " It will work, but significantly slower than it could.")
  }
}