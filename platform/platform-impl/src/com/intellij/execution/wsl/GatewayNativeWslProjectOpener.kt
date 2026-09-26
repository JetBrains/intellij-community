// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.configureToOpenDotIdeaOrCreateNewIfNotExists
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GatewayNativeWslProjectOpener {
  suspend fun openProject(wslDistribution: WSLDistribution, projectPath: String)

  companion object {
    @JvmStatic
    fun getInstanceIfAvailable(): GatewayNativeWslProjectOpener? = ApplicationManager.getApplication().getService(GatewayNativeWslProjectOpener::class.java)
  }
}

internal class GatewayNativeWslProjectOpenerImpl : GatewayNativeWslProjectOpener {
  override suspend fun openProject(wslDistribution: WSLDistribution, projectPath: String) {
    val pathToOpen = wslDistribution.getUNCRootPath().resolve(projectPath)
    ProjectUtil.openOrImportAsync(pathToOpen, OpenProjectTask {
      configureToOpenDotIdeaOrCreateNewIfNotExists(pathToOpen, null)
    })
  }
}