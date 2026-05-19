package com.intellij.platform.acp

import com.intellij.platform.eel.EelProcess
import kotlinx.coroutines.Deferred

/**
 * Handle to a running ACP agent process spawned by [AcpProcessLauncher.startProcess].
 *
 * Lifetime is owned by the scope passed to the launcher (`processLifetimeCoroutineScope`):
 * cancelling that scope terminates the process. [stopServer] requests a graceful shutdown
 * without affecting the scope. Observe [termination] to react to process death.
 */
interface AcpServerProcessHandler {
  /**
   * The underlying EEL process. Callers build the ACP transport (e.g. via
   * [com.intellij.ml.llm.agents.acp.AcpTransportUtil.createEelStdioTransport]) and the
   * [com.agentclientprotocol.protocol.Protocol] bound to its stdin/stdout themselves;
   * the handler stays a generic process holder and does not depend on ACP-protocol types.
   */
  val eelProcess: EelProcess

  /**
   * `true` while the process is alive and has not been asked to stop. Becomes `false`
   * after the process exits, after [stopServer] is invoked, or when the lifetime scope
   * is cancelled.
   */
  val isRunning: Boolean

  /**
   * Resolves once the process has terminated, with the outcome — explicit stop vs.
   * natural exit (with exit code and a snapshot of buffered stderr). Consumers that
   * want to react to unexpected process death should `await()` on this from their own
   * monitoring coroutine; see the example in [AcpProcessLauncher.startProcess].
   */
  val termination: Deferred<AcpProcessTermination>

  /**
   * Requests a graceful shutdown of the process — SIGTERM on POSIX with a short timeout
   * before SIGKILL; recursive kill on Windows. Fire-and-forget: the call returns
   * immediately while the actual kill runs on the process scope. [termination] resolves
   * with [AcpProcessTermination.StoppedExplicitly] once the process exits.
   */
  fun stopServer()
}
