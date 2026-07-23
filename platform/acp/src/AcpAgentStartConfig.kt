// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import com.intellij.platform.acp.impl.AcpAgentStartConfigImpl

/**
 * Configuration for starting an ACP agent process.
 *
 * Instances are produced by [AcpAgentStartConfig.create]. Callers that need to derive a new instance
 * from an existing one (e.g. with a resolved executable path) use [withCommand] / [withBaseArgs].
 *
 * @property command The executable path or command name (e.g., "node", "/usr/bin/python3", "/Users/User.Name/bin/my-agent"). Should NOT contain ~ for user home
 * @property baseArgs Command-line arguments to pass to run the agent, d.i. this will contain the package name when run with npx or uvx
 * @property env Environment variables to set for the process
 * @property args [baseArgs] concatenated with the internal acp-mode arguments. This is what the launcher actually passes to the process.
 */
interface AcpAgentStartConfig {
  val command: String
  val baseArgs: List<String>
  val env: Map<String, String>
  val args: List<String>

  fun withCommand(command: String): AcpAgentStartConfig
  fun withBaseArgs(baseArgs: List<String>): AcpAgentStartConfig

  companion object {
    /**
     * @param acpArgs Additional command-line arguments to pass to the agent to run it in acp mode.
     */
    fun create(
      command: String,
      baseArgs: List<String>,
      acpArgs: List<String>,
      env: Map<String, String>,
    ): AcpAgentStartConfig = AcpAgentStartConfigImpl(command, baseArgs, acpArgs, env)
  }
}
