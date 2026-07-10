// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import com.intellij.platform.acp.impl.AcpAgentStartConfigImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Configuration for starting an ACP agent process.
 *
 * Instances should be obtained from [AcpAgentsCatalog] via [AcpCatalogEntry.resolveStartConfig],
 * which resolves the agent's own launch details. The [create] factory is meant for catalog
 * providers producing those entries, not for general callers.
 *
 * @property command The executable path or command name (e.g., "node", "/usr/bin/python3", "/Users/User.Name/bin/my-agent"). Should NOT contain ~ for user home
 * @property baseArgs Command-line arguments to pass to run the agent, d.i. this will contain the package name when run with npx or uvx
 * @property acpArgs The agent's own args that start it as an ACP server (e.g. `--acp`), taken from the registry distribution or an `acp.json` entry.
 * @property env Environment variables to set for the process
 * @property args [baseArgs] concatenated with the internal acp-mode arguments. This is what the launcher actually passes to the process.
 */
interface AcpAgentStartConfig {
  val command: String
  val baseArgs: List<String>
  val acpArgs: List<String>
  val env: Map<String, String>
  val args: List<String>

  companion object {
    /**
     * Producer-only factory. Consumers should obtain a configuration from [AcpAgentsCatalog]
     * via [AcpCatalogEntry.resolveStartConfig] instead of calling this directly.
     */
    @ApiStatus.Internal
    fun create(
      command: String,
      baseArgs: List<String>,
      acpArgs: List<String>,
      env: Map<String, String>,
    ): AcpAgentStartConfig = AcpAgentStartConfigImpl(command, baseArgs, acpArgs, env)
  }
}
