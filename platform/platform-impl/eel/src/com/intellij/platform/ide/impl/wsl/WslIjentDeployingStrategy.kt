// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.ijent.spi.IjentConnectionStrategy
import com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategy
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class WslIjentDeployingStrategy(
  scope: CoroutineScope,
  override val ijentLabel: String,
  private val distribution: WSLDistribution,
  private val project: Project?,
  private val wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
) : IjentDeployingOverShellProcessStrategy(scope) {
  override suspend fun mapPath(path: Path): String? =
    distribution.getWslPath(path)

  @OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
  override suspend fun createShellProcess(): Process {
    // IJent can start an interactive shell by itself whenever it needs.
    // Enabling an interactive shell for IJent by default can bring problems, because stdio of IJent must not be populated
    // with possible user extensions in ~/.profile
    val wslCommandLineOptions = WSLCommandLineOptions()
      .setExecuteCommandInInteractiveShell(false)
      .setExecuteCommandInLoginShell(false)
      .setExecuteCommandInShell(false)

    wslCommandLineOptionsModifier(wslCommandLineOptions)

    val commandLine = WSLDistribution.neverRunTTYFix(GeneralCommandLine("/bin/sh"))
    distribution.doPatchCommandLine(commandLine, project, wslCommandLineOptions)

    return computeDetached { commandLine.createProcess() }
  }

  override suspend fun getConnectionStrategy(): IjentConnectionStrategy {
    return object : IjentConnectionStrategy {
      override suspend fun canUseVirtualSockets(): Boolean {
        return Registry.`is`("ijent.allow.hyperv.connection") && distribution.version == 2
      }
    }
  }
}