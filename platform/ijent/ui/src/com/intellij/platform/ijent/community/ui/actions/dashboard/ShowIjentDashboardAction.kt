// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentMachine
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.asSafely
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel

internal class ShowIjentDashboardAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Duplicated {
  override fun actionPerformed(e: AnActionEvent) {
    showDashboardDialog()
  }
}

private data class IjentData(
  val ijentSession: IjentSession,
  val ijentApi: IjentApi,
)

private fun listCachedEels(): List<IjentData> {
  return EelMachineResolver.getAll().flatMap { machineResolver ->
    machineResolver.getCachedDescriptors().mapNotNull { descriptor ->
      val ijentMachine = machineResolver.getResolvedEelMachine(descriptor) as? IjentMachine
      val cachedIjentSession = ijentMachine?.getCachedIjentSession()
      cachedIjentSession?.getIjentInstance(descriptor)?.let {
        IjentData(cachedIjentSession, it)
      }
    }
  }
}

private fun showDashboardDialog() {
  DialogBuilder()
    .apply { removeAllActions() }
    .apply { addCloseButton() }
    .title(IjentImplBundle.message("action.com.intellij.platform.ijent.community.ui.actions.dashboard.ShowIjentDashboardAction.text"))
    .centerPanel(ijentDashboardPanel())
    .showNotModal()
}

private fun ijentDashboardPanel(): JComponent {
  val listModel = DefaultListModel<IjentData>().apply {
    for (ijentData in listCachedEels()) {
      addElement(ijentData)
    }
  }

  val list = JBList(listModel).apply {
    emptyText.text = "No Ijent sessions running"
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = textListCellRenderer("") { ijentData ->
      val descriptor = ijentData.ijentApi.descriptor
      descriptor.asSafely<EelPathBoundDescriptor>()?.rootPath?.toString() ?: descriptor.name
    }
  }

  val splitter = OnePixelSplitter(false, "IjentDashboard.splitter", 0.3f).apply {
    firstComponent = ScrollPaneFactory.createScrollPane(list, true)
    secondComponent = detailPanel(null)
  }

  list.addListSelectionListener { event ->
    if (!event.valueIsAdjusting) {
      val idx = list.selectedIndex
      val ijentApi = if (idx >= 0) listModel.getElementAt(idx) else null
      splitter.secondComponent = detailPanel(ijentApi)
    }
  }

  if (!listModel.isEmpty) {
    list.selectedIndex = 0
  }

  return splitter
}

private fun detailPanel(ijentData: IjentData?): JComponent {
  return if (ijentData != null) {
    JBTabbedPane().also { tabbedPane ->
      IjentDashboardTab.EP_NAME.extensionList.forEach { tab ->
        val projects = ProjectManager.getInstance().openProjects.filter { it.getEelDescriptor() == ijentData.ijentApi.descriptor }
        val tabComponent = tab.createComponent(projects, ijentData.ijentApi, ijentData.ijentSession, tabbedPane)
        if (tabComponent != null) {
          tabbedPane.addTab(tab.name, tabComponent)
        }
      }
    }
  }
  else {
    JBPanel<JBPanel<*>>()
  }
}