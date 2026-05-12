package com.intellij.platform.acp.api

/**
 * Configuration for starting an ACP agent process.
 *
 * @property command The executable path or command name (e.g., "node", "/usr/bin/python3", "/Users/User.Name/bin/my-agent"). Should NOT contain ~ for user home
 * @property baseArgs Command-line arguments to pass to run the agent, d.i. this will contain the package name when run with npx or uvx
 * @property acpArgs Additional command-line arguments to pass to the agent to run it in acp mode
 * @property env Environment variables to set for the process
 */
data class AcpAgentStartConfig(
  val command: String,
  val baseArgs: List<String> = emptyList(),
  private val acpArgs: List<String> = emptyList(),
  val env: Map<String, String> = emptyMap(),
) {
  val args: List<String> get() = baseArgs + acpArgs
}
