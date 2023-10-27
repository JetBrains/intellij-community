// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.vscode

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.TransferSettingsConfiguration
import com.intellij.ide.customize.transferSettings.TransferSettingsDialog
import com.intellij.ide.customize.transferSettings.TransferableIdeId
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import com.intellij.ide.customize.transferSettings.providers.vscode.VSCodeSettingsProcessor.Companion.vsCodeHome
import com.intellij.ide.customize.transferSettings.showTransferSettingsDialog
import com.intellij.ide.customize.transferSettings.ui.representation.TransferSettingsRightPanelChooser
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.SmartList
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent

class VSCodeTransferSettingsProvider : TransferSettingsProvider {

  override val transferableIdeId = TransferableIdeId.VSCode

  private val processor = VSCodeSettingsProcessor()
  override val name: String
    get() = "Visual Studio Code"

  override fun isAvailable(): Boolean = true

  override fun getIdeVersions(skipIds: List<String>): SmartList<IdeVersion> = when (isVSCodeDetected()) {
    true -> SmartList(getIdeVersion())
    false -> SmartList()
  }

  private val cachedIdeVersion by lazy {
    IdeVersion(
      TransferableIdeId.VSCode,
      null,
      "VSCode",
      AllIcons.TransferSettings.Vscode,
      "Visual Studio Code",
      settingsInit = { processor.getProcessedSettings() },
      provider = this
    )
  }

  private fun getIdeVersion(): IdeVersion {
    return cachedIdeVersion
  }

  private fun isVSCodeDetected() = Files.isDirectory(Paths.get(vsCodeHome)) && processor.willDetectAtLeastSomething()

  override fun getRightPanel(ideV: IdeVersion, config: TransferSettingsConfiguration): TransferSettingsRightPanelChooser
    = VSCodeTransferSettingsRightPanelChooser(ideV, config)
}

private class VSCodeTransferSettingsRightPanelChooser(private val ide: IdeVersion, config: TransferSettingsConfiguration) : TransferSettingsRightPanelChooser(ide, config) {
  override fun getBottomComponentFactory(): () -> JComponent? = s@{
    if (!Registry.`is`("transferSettings.vscode.showRunningInfo")) {
      return@s null
    }
    if (ide.settingsCache.notes["vscode.databaseState"] != false) {
      return@s null
    }
    panel {
      row {
        icon(AllIcons.General.Warning).customize(UnscaledGaps(10))
        text(IdeBundle.message("transferSettings.vscode.file.warning"))
        link(IdeBundle.message("transferSettings.vscode.file.warning.action")) {
          val window = (it.source as? JComponent)?.let { DialogWrapper.findInstance(it) } as? TransferSettingsDialog
          if (window != null) {
            window.close(-1)
            showTransferSettingsDialog(window.project, null)
          }
        }
      }
    }
  }
}