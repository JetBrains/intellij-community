package com.intellij.database.datagrid

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.NonNls

interface CustomGridToolbarProvider {
  companion object {
    val EP_NAME: ExtensionPointName<CustomGridToolbarProvider> = ExtensionPointName.create("com.intellij.database.datagrid.customToolbarProvider")

    fun createToolbar(dataGrid: DataGrid, @NonNls place: String, actions: ActionGroup): ActionToolbar {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createToolbar(dataGrid, place, actions) }
             ?: ActionManager.getInstance().createActionToolbar(place, actions, true)
    }
  }

  fun createToolbar(dataGrid: DataGrid, @NonNls place: String, actions: ActionGroup): ActionToolbar?
}