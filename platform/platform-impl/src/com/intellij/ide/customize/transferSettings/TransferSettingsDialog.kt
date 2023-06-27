package com.intellij.ide.customize.transferSettings

import com.intellij.ide.customize.transferSettings.controllers.TransferSettingsListener
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.models.TransferSettingsModel
import com.intellij.ide.customize.transferSettings.ui.TransferSettingsProgressIndicatorBase
import com.intellij.ide.customize.transferSettings.ui.TransferSettingsView
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.application
import java.awt.event.ActionEvent
import javax.swing.*

class TransferSettingsDialog(private val project: Project,
                             private val config: TransferSettingsConfiguration) : DialogWrapper(project) {
  private val model: TransferSettingsModel = TransferSettingsModel(config, true)
  private val view = TransferSettingsView(config, model)
  private val progressBar = JProgressBar(0, 100)
  private val status = JLabel("No yet status")
  private val successOrFailureLabel = JLabel().apply { isVisible = false }
  private val progressBase = TransferSettingsProgressIndicatorBase(progressBar, status, successOrFailureLabel)
  private var importPerformed = false
  private val importAction = object : DialogWrapperAction("Import") {
    override fun doAction(e: ActionEvent) {
      if (importPerformed) {
        close(OK_EXIT_CODE)
      }
      val selectedIde = view.selectedIde as? IdeVersion ?: error("Selected ide is null or not IdeVersion")
      config.controller.performImport(project, selectedIde, true, progressBase)
    }
  }


  init {
    init()
    setSize(640, 480)

    config.controller.addListener(object : TransferSettingsListener {
      override fun importStarted(ideVersion: IdeVersion, settings: Settings) {
        successOrFailureLabel.isVisible = false
        getButton(importAction)?.isEnabled = false
      }

      override fun importFailed(ideVersion: IdeVersion, settings: Settings, throwable: Throwable) {
        successOrFailureLabel.isVisible = true
        successOrFailureLabel.text = "Failed"
        updateImportButton()
      }

      override fun importPerformed(ideVersion: IdeVersion, settings: Settings) {
        successOrFailureLabel.isVisible = true
        successOrFailureLabel.text = "Success"
        progressBar.isVisible = false
        updateImportButton()
      }

      private fun updateImportButton() {
        val button = getButton(importAction) ?: return
        importPerformed = true
        button.isEnabled = true
        button.text = "Close"
      }
    })
  }


  override fun createCenterPanel(): JComponent {
    application.assertIsDispatchThread()

    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
      add(view.panel)
      add(status)
      add(successOrFailureLabel)
      add(progressBar)
    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(importAction, cancelAction)
  }
}

