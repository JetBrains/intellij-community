// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.importer

import com.intellij.importSettings.data.*
import com.intellij.openapi.ui.DialogWrapper
import java.awt.event.ActionEvent
import javax.swing.Action

fun createDialog(provider: ActionsDataProvider, product: ImportItem): DialogWrapper {
  when(provider) {
    is SyncActionsDataProvider -> return SyncSettingDialog(provider, product)
    is JBrActionsDataProvider -> return ImportSettingDialog(provider, product)
    is ExtActionsDataProvider -> return ImportSettingDialog(provider, product)
    else -> throw Exception("unexpected provider type")
  }
}

fun createDialog(provider: SyncActionsDataProvider, product: ImportItem): DialogWrapper {
  return SyncSettingDialog(provider, product)
}

class ImportSettingDialog(val provider: ActionsDataProvider, product: ImportItem): SettingDialog(provider, product)  {
  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Import Settings")
    }
  }

  override fun applyFields() {
    super.applyFields()
    val productService = provider.productService
/*    if(productService is ConfigurableImport) {
      //productService.importSettings(product.id)
    }*/
  }

  override fun getCancelAction(): Action {
    return super.getCancelAction().apply {
      putValue(Action.NAME, "Back")
    }
  }
}

class SyncSettingDialog(val provider: SyncActionsDataProvider, product: ImportItem) : SettingDialog(provider, product) {
  override val configurable = false

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, getBackAction(), getImportAction())
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Sync Settings")
    }
  }

  override fun applyFields() {
    super.applyFields()
    provider.productService.syncSettings(product.id)
  }

  private fun getImportAction(): Action {
    return object : DialogWrapperAction("Import Once") {

      override fun doAction(e: ActionEvent?) {
        provider.productService.importSettings(product.id)
        close(OK_EXIT_CODE)
      }
    }
  }

  private fun getBackAction(): Action {
    return object : DialogWrapperAction("Back") {

      override fun doAction(e: ActionEvent?) {
        close(CANCEL_EXIT_CODE)
      }
    }
  }
}