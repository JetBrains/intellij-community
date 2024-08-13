// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.icons.AllIcons
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.SmartList
import com.intellij.util.containers.FactoryMap
import java.awt.BorderLayout
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

internal class RunDashboardTypePanel(private val project: Project) : NonOpaquePanel(BorderLayout()), UiDataProvider {
  var type: ConfigurationType? = null
    set(value) {
      field = value
      updateTree(value)
    }
  private var hasFolders = false

  private val root = CheckedTreeNode("TypeRoot")
  private val tree = CheckboxTree(object : CheckboxTree.CheckboxTreeCellRenderer() {
    override fun customizeRenderer(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
      if (value is CheckedTreeNode) {
        val userObject = value.userObject
        if (userObject is RunnerAndConfigurationSettings) {
          val renderer = textRenderer
          renderer.icon = type?.icon
          renderer.append(userObject.name)
        }
        else if (userObject is String) {
          val renderer = textRenderer
          renderer.icon = AllIcons.Nodes.Folder
          renderer.append(userObject)
        }
      }
    }
  }, root)
  private val treeExpander = DefaultTreeExpander(tree)

  init {
    project.getMessageBus().connect(project).subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
      private var updateInProgress = false

      override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        if (!updateInProgress) {
          updateTree(type)
        }
      }

      override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        if (!updateInProgress) {
          updateTree(type)
        }
      }

      override fun beginUpdate() {
        updateInProgress = true
      }

      override fun endUpdate() {
        updateInProgress = false
        updateTree(type)
      }
    })

    tree.addCheckboxTreeListener(object : CheckboxTreeListener {
      override fun nodeStateChanged(node: CheckedTreeNode) {
        val settings = node.userObject as? RunnerAndConfigurationSettings ?: return
        if (node.userObject is RunnerAndConfigurationSettings) {
          if (node.isChecked) {
            (RunDashboardManager.getInstance(project) as RunDashboardManagerImpl).restoreConfigurations(
              SmartList(settings.getConfiguration()))
          }
          else {
            (RunDashboardManager.getInstance(project) as RunDashboardManagerImpl).hideConfigurations(
              SmartList(settings.getConfiguration()))
          }
        }
      }
    })

    val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
    add(scrollPane, BorderLayout.CENTER)
    RunDashboardManagerImpl.setupToolbar(this, scrollPane, project)
  }

  private fun updateTree(type: ConfigurationType?) {
    if (type == null) {
      hasFolders = false
      root.removeAllChildren()
      (tree.model as DefaultTreeModel).setRoot(root)
      return
    }

    val children = ArrayList<CheckedTreeNode>()
    val folders = FactoryMap.create<String, CheckedTreeNode> { CheckedTreeNode(it) }
    val settingsList = RunManager.getInstance(project).getConfigurationSettingsList(type)
    val runDashboardManager = RunDashboardManager.getInstance(project)
    for (settings in settingsList) {
      val node = CheckedTreeNode(settings)
      if (!runDashboardManager.isShowInDashboard(settings.configuration)) {
        node.isChecked = false
      }

      val folderName = settings.folderName
      if (folderName != null) {
        folders[folderName]!!.add(node)
      }
      else {
        children.add(node)
      }
    }

    root.removeAllChildren()
    val folderNodes = ArrayList(folders.values)
    folderNodes.sortBy { it.userObject as String }
    folderNodes.forEach(root::add)
    children.forEach(root::add)
    (tree.model as DefaultTreeModel).setRoot(root)
    hasFolders = folderNodes.isNotEmpty()
    for (folderNode in folderNodes) {
      tree.expandPath(TreePath(folderNode.path))
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER] = true
    sink[PlatformDataKeys.TREE_EXPANDER] = if (hasFolders) treeExpander else null
  }
}