// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp.impl

import com.intellij.platform.acp.AcpAgentStartConfig

internal class AcpAgentStartConfigImpl(
  override val command: String,
  override val baseArgs: List<String>,
  private val acpArgs: List<String>,
  override val env: Map<String, String>,
) : AcpAgentStartConfig {
  override val args: List<String> get() = baseArgs + acpArgs

  override fun withCommand(command: String): AcpAgentStartConfig =
    AcpAgentStartConfigImpl(command, baseArgs, acpArgs, env)

  override fun withBaseArgs(baseArgs: List<String>): AcpAgentStartConfig =
    AcpAgentStartConfigImpl(command, baseArgs, acpArgs, env)
}
