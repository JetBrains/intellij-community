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
   * The underlying EEL process. Callers are responsible for building an ACP
   * [com.agentclientprotocol.transport.StdioTransport] over its `stdout` / `stdin`
   * (its Flow + suspend-writer constructor is the non-blocking entry point) and
   * wiring it into [com.agentclientprotocol.protocol.Protocol] themselves — the
   * handler stays a generic process holder and does not depend on ACP-protocol types.
   *
   * A minimal reference adapter — illustrative, copy and adjust to the caller's
   * logging/error-handling needs:
   *
   * ```
   * fun eelStdioTransport(scope: CoroutineScope, eelProcess: EelProcess): Transport {
   *   val input: Flow<String> = eelProcess.stdout.lines(Charsets.UTF_8)
   *     .onCompletion {
   *       // Close eel channels once the read path is done — covers transport close,
   *       // natural EOF, and read error. Closing stdin signals EOF to the agent.
   *       runCatching { eelProcess.stdout.closeForReceive() }
   *       runCatching { eelProcess.stdin.close(null) }
   *     }
   *
   *   val output: suspend (String) -> Unit = { line ->
   *     val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
   *     try {
   *       eelProcess.stdin.sendWholeBuffer(ByteBuffer.wrap(bytes))
   *     }
   *     catch (e: EelSendChannelException) {
   *       // Surface channel-closed as plain IOException so the library's write loop
   *       // treats it as a clean close instead of firing onError.
   *       throw IOException("Eel send channel closed", e)
   *     }
   *   }
   *
   *   return StdioTransport(scope, Dispatchers.IO, input, output)
   * }
   * ```
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
