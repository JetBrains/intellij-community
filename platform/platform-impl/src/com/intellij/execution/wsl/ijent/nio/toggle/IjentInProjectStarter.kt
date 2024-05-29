// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.*

/** Starts the IJent if a project on WSL is opened. */
internal class IjentInProjectStarter : ProjectActivity {
  override suspend fun execute(project: Project) {
    val service = serviceAsync<IjentWslNioFsToggler>()
    if (!service.isInitialized) {
      return
    }

    val allWslDistributions = service.coroutineScope.async {
      withContext(Dispatchers.IO) {
        serviceAsync<WslDistributionManager>().installedDistributions
      }
    }

    val relatedWslDistributions = hashSetOf<WSLDistribution>()
    for (module in project.modules) {
      for (contentRoot in module.rootManager.contentRoots) {
        val path =
          try {
            contentRoot.toNioPath()
          }
          catch (_: UnsupportedOperationException) {
            continue
          }


        for (distro in allWslDistributions.await()) {
          val matches =
            try {
              distro.getWslPath(path) != null
            }
            catch (_: IllegalArgumentException) {
              false
            }
          if (matches) {
            relatedWslDistributions += distro
          }
        }
      }
    }

    for (distro in relatedWslDistributions) {
      service.coroutineScope.launch {
        serviceAsync<WslIjentManager>().getIjentApi(distro, project, false)
      }
    }
  }
}
