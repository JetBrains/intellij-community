// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings

import com.intellij.ide.customize.transferSettings.controllers.TransferSettingsListener
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.models.TransferSettingsModel
import com.intellij.ide.customize.transferSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.ide.customize.transferSettings.ui.TransferSettingsProgressIndicatorBase
import com.intellij.ide.customize.transferSettings.ui.TransferSettingsView
import com.intellij.openapi.project.Project
import com.intellij.util.application
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JProgressBar

class TransferSettingsFacade(private val project: Project?) {
  private val config = DefaultTransferSettingsConfiguration(TransferSettingsDataProvider(VSCodeTransferSettingsProvider()), false)
  val hasVsCode = (config.dataProvider.orderedIdeVersions.firstOrNull() as? IdeVersion)?.provider is VSCodeTransferSettingsProvider

  private val model: TransferSettingsModel = TransferSettingsModel(config, true)

  val button by lazy { JButton("Import") }
  val status by lazy { JLabel("No yet status") }
  val progressBar by lazy { JProgressBar(0, 100) }
  val successOrFailureLabel by lazy { JLabel().apply { isVisible = false } }
  private val progressBase by lazy { TransferSettingsProgressIndicatorBase(progressBar, status, successOrFailureLabel) }

  val view by lazy { initView() }

  private fun initView(): TransferSettingsView {
    application.assertIsDispatchThread()

    val view = TransferSettingsView(config, model)

    button.addActionListener {
      val selectedIde = view.selectedIde as? IdeVersion ?: error("Selected ide is null or not IdeVersion")
      config.controller.performImport(project, selectedIde, true, progressBase)
    }

    config.controller.addListener(object : TransferSettingsListener {
      override fun importStarted(ideVersion: IdeVersion, settings: Settings) {
        successOrFailureLabel.isVisible = false
        button.isEnabled = false
      }

      override fun importFailed(ideVersion: IdeVersion, settings: Settings, throwable: Throwable) {
        successOrFailureLabel.isVisible = true
        successOrFailureLabel.text = "Failed"
        button.isEnabled = true
      }

      override fun importPerformed(ideVersion: IdeVersion, settings: Settings) {
        successOrFailureLabel.isVisible = true
        successOrFailureLabel.text = "Success"
        progressBar.isVisible = false
      }
    })

    return view
  }
}