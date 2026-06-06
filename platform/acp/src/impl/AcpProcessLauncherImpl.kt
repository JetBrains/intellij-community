// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.acp.AcpAgentStartConfig
import com.intellij.platform.acp.AcpProcessLaunchException
import com.intellij.platform.acp.AcpProcessLauncher
import com.intellij.platform.acp.AcpProcessLauncherProvider
import com.intellij.platform.acp.AcpServerProcessHandler
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/**
 * Platform-owned [com.intellij.platform.acp.AcpProcessLauncher] service. Delegates to the first currently loaded
 * [com.intellij.platform.acp.AcpProcessLauncherProvider]. Without the AI Assistant plugin there is no ACP runtime,
 * so every launch attempt fails fast with a clear message.
 */
internal class AcpProcessLauncherImpl(private val project: Project) : AcpProcessLauncher {
  override suspend fun startProcess(
    agentName: String,
    config: AcpAgentStartConfig,
    projectDir: Path,
    processLifetimeCoroutineScope: CoroutineScope,
  ): AcpServerProcessHandler {
    val launcher = AcpProcessLauncherProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.createLauncher(project) }
    if (launcher == null) throw AcpProcessLaunchException.AiAssistantPluginMissing(agentName)
    return launcher.startProcess(agentName, config, projectDir, processLifetimeCoroutineScope)
  }
}
