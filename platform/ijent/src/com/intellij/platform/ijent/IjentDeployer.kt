// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentDeployer")

package com.intellij.platform.ijent

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

/**
 * Describes the IJent deployment state for a machine.
 *
 * [Deployed] means that the machine has a cached session. Use [IjentSession.isRunning] to check if the session is running.
 */
@VisibleToClasses(
  "com.intellij.docker.ijent.DockerEelMachine",
  "com.intellij.execution.wsl.WSLDistributionTest",
  "com.intellij.execution.wsl.WslIjentManager",
  "com.intellij.platform.ide.impl.wsl.ProductionWslIjentManager",
  "com.intellij.platform.ide.impl.wsl.WslEelMachine",
  "com.intellij.platform.ijent.community.ui.actions.unavailable.IjentUnavailableDialogHandler",
  "com.intellij.platform.ijent.community.ui.actions.unavailable.IjentUnavailableDialogKt",
  "com.intellij.platform.ijent.testFramework.functional.fixture.EelFixtureWslIjentManager",
)
sealed interface IjentSessionState {
  data object NotDeployed : IjentSessionState
  data object Deploying : IjentSessionState
  data class Deployed(val session: IjentSession) : IjentSessionState
  data class Failed(val cause: Throwable) : IjentSessionState
}

@get:VisibleToClasses(
  "com.intellij.docker.ijent.DockerEelMachine",
  "com.intellij.platform.ijent.community.ui.actions.unavailable.IjentUnavailableDialogHandler",
)
val IjentSessionState.runningIjentSessionOrNull: IjentSession? get() {
  return (this as? IjentSessionState.Deployed)?.session?.takeIf { it.isRunning }
}

interface IjentMachine : EelMachine {
  val ijentSessionState: StateFlow<IjentSessionState>
  fun getCachedIjentSession(): IjentSession? = ijentSessionState.value.runningIjentSessionOrNull
  suspend fun getIjentSession(sessionScope: ParentOfIjentScopes): IjentSession

  /** `false` when the backend is known-gone and callers should skip remote operations that would otherwise spin up a session. */
  @VisibleToClasses(
    "com.intellij.platform.ijent.community.impl.nio.AbsoluteIjentNioPath",
  )
  fun isBackendAvailable(): Boolean = true
}

interface IjentSession {
  val isRunning: Boolean
  val platform: EelPlatform
  val remotePathToBinary: String  // TODO Use IjentPath.Absolute.
  val ijentProcessInfo: IjentProcessInfo

  /**
   * `false` while the transport is down on a live session and the reconnect layer is recovering it.
   *
   * A dead session keeps its last value, which may be `true`, so check [isRunning] first.
   * Always `true` without a reconnect layer (stdio, reconnects off): there is nothing to recover.
   */
  val transportConnected: StateFlow<Boolean>

  /**
   * A scope for the process. Use very cautiously, it's not a supervisor scope, it can destroy the whole communication.
   */
  @DelicateCoroutinesApi
  @get:VisibleToClasses(
    "com.intellij.docker.ijent.DockerEelMachine",
    "com.intellij.platform.ide.impl.wsl.WslIjentUtil",
    "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategyUnitTest",
    "com.intellij.platform.ijent.ssh.AbstractIjentSshManagerTest",
    "com.intellij.platform.ijent.ssh.impl.AbstractIjentSshManager",
  )
  val sessionCoroutineScope: IjentScope

  @VisibleToClasses(
    "com.intellij.diagnostic.logs.ijent.IjentLogLevelListener",
  )
  suspend fun updateLogLevel()

  /**
   * Points IJent's parent-death watch at [pid] — the process that owns this session's lifetime. When that process
   * dies, IJent loses the right to outlive its disconnects and exits with its last client. Calling again replaces
   * the watch and restores the privilege, which is how a relaunched owner takes the session back.
   */
  @VisibleToClasses(
      "com.intellij.rd.eel.frontend.IjentBackendWatchArmer",
  )
  suspend fun setParentProcessToWatch(pid: Long)

  @VisibleToClasses(
    "com.intellij.docker.ijent.DockerEelMachine",
    "com.intellij.platform.ide.impl.wsl.ProductionWslIjentManager",
    "com.intellij.platform.ijent.community.ui.actions.unavailable.IjentUnavailableDialogHandler",
    "com.intellij.platform.ijent.impl.base.GrpcIjentApiPosix",
    "com.intellij.platform.ijent.impl.base.GrpcIjentApiWindows",
    "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategyUnitTest",
    "com.intellij.platform.ijent.ssh.AbstractIjentSshManagerTest",
    "com.intellij.platform.ijent.ssh.daemon.IjentOverJetBrainsDaemonManager",
    "com.intellij.platform.ijent.ssh.impl.AbstractIjentSshManager",
  )
  fun close()

  fun getIjentInstance(descriptor: EelDescriptor): IjentApi

  val eventBus: IjentEventBus

  @VisibleToClasses(
    "com.intellij.platform.ijent.impl.base.GrpcIjentSessionProviderKt",
  )
  enum class LogLevel {
    INFO, DEBUG, TRACE
  }

  interface Posix : IjentSession {
    override fun getIjentInstance(descriptor: EelDescriptor): IjentPosixApi
    suspend fun requestHyperVTransports(vmId: UUID): Boolean
  }

  interface Windows : IjentSession {
    override fun getIjentInstance(descriptor: EelDescriptor): IjentWindowsApi
  }
}

/**
 * Suspends until the session ends.
 * Returns a copy of its exit reason, see [IjentScope.resolveExitReason], or `null` when none was recorded.
 */
@ApiStatus.Internal
@OptIn(DelicateCoroutinesApi::class)
@VisibleToClasses(
  "com.intellij.platform.frontend.split.baseline.light.LightModeConnectionStatusService",
  "com.intellij.platform.ijent.IjentSessionExitReasonTest",
)
suspend fun IjentSession.awaitEnd(): EelUnavailableException? {
  val ijentScope = sessionCoroutineScope
  ijentScope.s.coroutineContext.job.join()
  return ijentScope.resolveExitReason() as EelUnavailableException?
}