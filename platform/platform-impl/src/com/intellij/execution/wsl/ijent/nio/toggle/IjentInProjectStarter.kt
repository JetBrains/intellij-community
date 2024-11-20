// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Starts the IJent if a project on WSL is opened.
 *
 * At the moment of writing this string,
 * this class was just an optimization handler that speeds up sometimes the first request to the IJent.
 * It was not necessary for running the IDE.
 *
 * See also [IjentWslFileSystemApplicationActivity].
 */
internal class IjentInProjectStarter : ProjectActivity {
  override suspend fun execute(project: Project): Unit = coroutineScope {
    if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
      return@coroutineScope
    }

    val ijentWslNioFsToggler = IjentWslNioFsToggler.instanceAsync()
    if (!ijentWslNioFsToggler.isAvailable) {
      return@coroutineScope
    }

    val allWslDistributions = async(Dispatchers.IO) {
      serviceAsync<WslDistributionManager>().installedDistributions
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
      launch {
        serviceAsync<WslIjentManager>().getIjentApi(distro, project, false)
      }
    }
  }
}
