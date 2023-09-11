// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.actions

import com.intellij.importSettings.data.JBrActionsDataProvider
import com.intellij.importSettings.data.SettingsService
import com.intellij.importSettings.importer.SettingDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper

class ConfigAction(val callback: (Int) -> Unit) : DumbAwareAction() {
  val service = SettingsService.getInstance().getJbService()
  public val config
    get() = service.getConfig()


  init {
    templatePresentation.text = config.name
    templatePresentation.icon = service.getProductIcon(config.id)
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {
    callback(DialogWrapper.OK_EXIT_CODE)

    val dialog = SettingDialog(JBrActionsDataProvider.getInstance(), config)
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()

  }
}