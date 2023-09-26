// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.actions

import com.intellij.importSettings.data.ActionsDataProvider
import com.intellij.importSettings.data.Product
import com.intellij.importSettings.importer.SettingDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper

class SettingChooserItemAction(val product: Product, val provider: ActionsDataProvider, val callback: (Int) -> Unit) : DumbAwareAction() {

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = true
    e.presentation.text = provider.getText(product)
    e.presentation.icon = provider.getProductIcon(product.id)
  }

  override fun actionPerformed(e: AnActionEvent) {
    callback(DialogWrapper.OK_EXIT_CODE)

    val dialog = SettingDialog(provider, product)
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()

  }

}