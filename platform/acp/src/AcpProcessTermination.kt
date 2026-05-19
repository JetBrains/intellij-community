// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

/**
 * Outcome of an ACP agent process exit. Resolved via [AcpServerProcessHandler.termination].
 */
sealed interface AcpProcessTermination {
  /**
   * The process was stopped via [AcpServerProcessHandler.stopServer]
   * or an internal failure path (e.g. stderr reader fault, scope cancellation). The exit code is not
   * surfaced because the termination was driven by us, not the agent.
   */
  data object StoppedExplicitly : AcpProcessTermination

  /**
   * The process exited on its own. [exitCode] may be 0 (clean exit) or non-zero (crash). Consumers
   * decide whether a non-zero exit warrants user-visible reporting.
   *
   * [stderr] is a snapshot of the buffered stderr captured up to the exit, bounded to the handler's
   * stderr buffer size (latest content if the buffer overflowed).
   */
  data class Exited(val exitCode: Int, val stderr: String) : AcpProcessTermination
}
