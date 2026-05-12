// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp.api

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/**
 * Fallback [AcpProcessLauncher] shipped with the platform so the service interface is always
 * resolvable. The real implementation lives in the AI Assistant plugin and overrides this one
 * via `overrides="true"`. Without that plugin there is no ACP runtime, so every launch attempt
 * fails fast with a clear message.
 */
internal class DefaultAcpProcessLauncher(@Suppress("unused") private val project: Project) : AcpProcessLauncher {
  override suspend fun startProcess(
    agentName: String,
    config: AcpAgentStartConfig,
    projectDir: Path,
    processLifetimeCoroutineScope: CoroutineScope,
  ): AcpServerProcessHandler {
    throw AcpProcessLaunchException(
      "Cannot launch ACP agent '$agentName': the AI Assistant plugin is required to run ACP agents."
    )
  }
}
