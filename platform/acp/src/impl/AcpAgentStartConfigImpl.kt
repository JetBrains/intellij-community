// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp.impl

import com.intellij.platform.acp.AcpAgentStartConfig
import java.nio.file.Path

internal class AcpAgentStartConfigImpl(
  override val command: String,
  override val baseArgs: List<String>,
  override val acpArgs: List<String>,
  override val env: Map<String, String>,
  override val workingDir: Path? = null,
) : AcpAgentStartConfig {
  override val args: List<String> get() = baseArgs + acpArgs
}
