// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.rpc

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.application
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

/**
 * Internal action to display all registered Remote APIs in the RemoteApiRegistry.
 */
internal class ShowRegisteredRemoteApisAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val registry = application.serviceOrNull<RemoteApiProviderService>() ?: return

    @Suppress("TestOnlyProblems")
    val apiList = registry.listRegisteredApis()
    ShowRegisteredApisDialog(apiList).show()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class ShowRegisteredApisDialog(private val apis: List<String>) : DialogWrapper(null) {
    init {
      title = "Registered Remote APIs"
      init()
    }

    override fun createCenterPanel(): JComponent {
      val tableModel = DefaultTableModel(arrayOf("API FQN"), 0)
      apis.sorted().forEach { api ->
        tableModel.addRow(arrayOf(api))
      }

      val table = JBTable(tableModel).apply {
        setShowGrid(false)
        isStriped = true
      }

      return JBScrollPane(table).apply {
        preferredSize = Dimension(600, 400)
      }
    }
  }
}
