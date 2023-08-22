package com.intellij.ide.customize.transferSettings

import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.controllers.TransferSettingsListener
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.models.TransferSettingsModel
import com.intellij.ide.customize.transferSettings.ui.TransferSettingsProgressIndicatorBase
import com.intellij.ide.customize.transferSettings.ui.TransferSettingsView
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ClientProperty
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.event.ActionEvent
import javax.swing.*

class TransferSettingsDialog(private val project: Project,
                             private val config: TransferSettingsConfiguration) : DialogWrapper(project) {
  private val model: TransferSettingsModel = TransferSettingsModel(config, true)
  private val view = TransferSettingsView(config, model)
  private val progressBar = JProgressBar(0, 100).apply {
    isVisible = false
  }
  private val status = JLabel()
  private val progressBase = TransferSettingsProgressIndicatorBase(progressBar, status, status)
  private var importPerformed = false
  private val importAction = object : DialogWrapperAction(IdeBundle.message("transfersettings.dialog.button.import")) {
    override fun doAction(e: ActionEvent) {
      if (importPerformed) {
        close(13)
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
        getButton(importAction)?.isEnabled = false
        progressBar.isVisible = true
      }

      override fun importFailed(ideVersion: IdeVersion, settings: Settings, throwable: Throwable) {
        progressBar.isVisible = false
        updateImportButton()
      }

      override fun importPerformed(ideVersion: IdeVersion, settings: Settings) {
        progressBar.isVisible = false
        updateImportButton()
        neverShowTransferSettingsBalloonAgain()
      }

      private fun updateImportButton() {
        val button = getButton(importAction) ?: return
        importPerformed = true
        button.isEnabled = true
        button.text = IdeBundle.message("transfersettings.dialog.button.close")
        getButton(cancelAction)?.let {
          it.isVisible = false
        }
      }
    })
  }

  override fun isOK(): Boolean {
    return exitCode == 13
  }

  @RequiresEdt
  override fun createCenterPanel(): JComponent {
    return BorderLayoutPanel().apply {
      addToCenter(view.panel)
    }
  }

  override fun createSouthPanel(): JComponent {
    val panel = super.createSouthPanel()
    getButton(importAction)?.let {
      ClientProperty.put(it, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    }

    return panel
  }

  override fun createSouthPanel(leftSideButtons: MutableList<out JButton>,
                                rightSideButtons: MutableList<out JButton>,
                                addHelpToLeftSide: Boolean): JPanel {
    val buttons = super.createSouthPanel(leftSideButtons, rightSideButtons, addHelpToLeftSide)

    return BorderLayoutPanel().apply {
      addToCenter(BorderLayoutPanel().apply {
        border = JBUI.Borders.emptyTop(3)
        addToLeft(Box.createHorizontalGlue())
        addToCenter(status.apply {
          horizontalAlignment = SwingConstants.RIGHT
        })
        addToRight(progressBar)
      })
      addToRight(buttons)

    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(importAction, cancelAction)
  }
}

