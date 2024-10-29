// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.target

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.sync.WslHashFilters.WslHashFiltersBuilder
import com.intellij.execution.wsl.sync.WslHashMatcher.Factory.extensions
import com.intellij.execution.wsl.sync.WslSync
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.TimeoutUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.JOptionPane


/**
 * Demonstrates how [WslSync.syncWslFolders] works
 */
@ApiStatus.Internal
@Suppress("HardCodedStringLiteral") // This is a test, internal only action
class WslSyncAction : DumbAwareAction("WSL Sync") {
  override fun actionPerformed(e: AnActionEvent) {

    val progressManager = ProgressManager.getInstance()
    val distroNames = ArrayList<String>()

    progressManager.run(object : Task.Modal(e.project, "Getting List of Distros", false) {
      override fun run(indicator: ProgressIndicator) {
        distroNames += WslDistributionManager.getInstance().installedDistributions.map { it.presentableName }
      }
    })
    if (distroNames.isEmpty()) {
      throw Exception("Please install WSL")
    }

    val distroName = JOptionPane.showInputDialog(null, "Choose distro", "Distro", JOptionPane.QUESTION_MESSAGE, null, distroNames.toArray(),
                                                 distroNames[0])

    val directions = arrayOf("Linux -> Windows", "Windows -> Linux")
    val linToWin = directions[0]

    val direction = JOptionPane.showInputDialog(null, "Choose direction", "Direction", JOptionPane.QUESTION_MESSAGE, null, directions,
                                                linToWin)

    val linux = JOptionPane.showInputDialog("Linux path", "/home/link/huge_folder")
    val windows = Path.of(JOptionPane.showInputDialog("Windows path", "c:\\temp\\huge_folder"))
    val extensionsStr = JOptionPane.showInputDialog("Comma separated extensions (or nothing for all)", "py,pyi").trim()
    val includeExtensions = if (extensionsStr.isNotBlank()) extensionsStr.split(',').toTypedArray() else arrayOf()

    progressManager.run(object : Task.Modal(e.project, "Syncing Folders..", false) {
      override fun run(indicator: ProgressIndicator) {
        val distro = WslDistributionManager.getInstance().installedDistributions.first { it.presentableName == distroName }
        try {
          val time = TimeoutUtil.measureExecutionTime<Exception> {
            val filters = WslHashFiltersBuilder().include(*extensions(*includeExtensions)).build()
            WslSync.syncWslFolders(linux, windows, distro, direction == linToWin, filters)
          }
          val message = "Synced in $time"
          JOptionPane.showMessageDialog(null, message)
          logger<WslSyncAction>().warn(message)
        }
        catch (e: Exception) {
          JOptionPane.showMessageDialog(null, "Error running sync. Check logs", "Error", JOptionPane.ERROR_MESSAGE)
          logger<WslSyncAction>().warn(e)
        }
      }
    })
  }
}
