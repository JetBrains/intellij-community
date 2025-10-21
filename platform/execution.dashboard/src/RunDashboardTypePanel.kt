// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManagerProxy
import com.intellij.icons.AllIcons
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardConfigurationDto
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceRpc
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardManager
import com.intellij.platform.execution.dashboard.splitApi.frontend.RunDashboardUiUtils
import com.intellij.platform.project.projectId
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.containers.FactoryMap
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JEditorPane
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

@ApiStatus.Internal
class RunDashboardTypePanel(private val project: Project) : NonOpaquePanel(BorderLayout()), UiDataProvider {
  var type: ConfigurationType? = null
    set(value) {
      field = value
      if (nodeStateChanging) return

      if (value != null) {
        checkBox.isSelected = !RunDashboardManagerProxy.getInstance(project).isNewExcluded(value.id)
        applyLink.isEnabled = hasTypeWithOppositeExclusion(value.id)
      }
      updateTree(value)
    }
  private var nodeStateChanging = false
  private lateinit var checkBox: JCheckBox
  private lateinit var applyLink: JEditorPane
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
        if (userObject is RunDashboardConfigurationDto) {
          val renderer = textRenderer
          renderer.icon = type?.icon
          @Suppress("HardCodedStringLiteral")
          renderer.append(userObject.configurationDisplayName)
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
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardServiceRpc.getInstance().getRunManagerUpdates(project.projectId()).collect {
        updateTree(type)
      }
    }

    tree.addCheckboxTreeListener(object : CheckboxTreeListener {
      override fun nodeStateChanged(node: CheckedTreeNode) {
        val settings = node.userObject as? RunDashboardConfigurationDto ?: return
        if (node.userObject is RunDashboardConfigurationDto) {
          try {
            nodeStateChanging = true
            if (node.isChecked) {
              RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
                RunDashboardServiceRpc.getInstance().restoreConfigurations(project.projectId(), listOf(settings.configurationId))
              }
            }
            else {
              RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
                RunDashboardServiceRpc.getInstance().hideConfigurations(project.projectId(), listOf(settings.configurationId))
              }
            }
          }
          finally {
            nodeStateChanging = false
          }
        }
      }
    })

    val typePanel = panel {
      row {
        checkBox(ExecutionBundle.message("run.dashboard.show.new.configurations")).applyToComponent {
          checkBox = this
          addActionListener { _ ->
            val type = type
            if (type != null) {
              RunDashboardManagerProxy.getInstance(project).setNewExcluded(type.id, !isSelected)
              applyLink.isEnabled = hasTypeWithOppositeExclusion(type.id)
            }
          }
        }
          .comment(ExecutionBundle.message("run.dashboard.apply.to.all.types")) {
            val manager = RunDashboardManagerProxy.getInstance(project)
            val isChecked = checkBox.isSelected
            for (typeId in manager.types) {
              manager.setNewExcluded(typeId, !isChecked)
            }
            val type = type
            if (type != null) {
              applyLink.isEnabled = hasTypeWithOppositeExclusion(type.id)
            }
          }
          .also {
            applyLink = it.comment!!
          }
      }
    }
    typePanel.border = JBUI.Borders.empty(1, UIUtil.DEFAULT_HGAP) // align with the tree's first row

    val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
    val wrapper = NonOpaquePanel(BorderLayout())
    wrapper.add(typePanel, BorderLayout.EAST)
    wrapper.add(scrollPane, BorderLayout.CENTER)
    add(wrapper, BorderLayout.CENTER)

    RunDashboardUiUtils.setupToolbar(this, wrapper, project)
  }

  private fun shouldShowConfigurationInDashboard(configurationDto: RunDashboardConfigurationDto): Boolean {
    return FrontendRunDashboardManager.getInstance(project).getServices().any { frontendService ->
      frontendService.name == configurationDto.configurationDisplayName
      && frontendService.typeId == configurationDto.configurationTypeId
    }
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
    val frontendSettingsList = FrontendRunDashboardManager.getInstance(project).getAvailableConfigurations().filter { it.configurationTypeId == type.id }
    for (settings in frontendSettingsList) {
      val node = CheckedTreeNode(settings)
      if (!shouldShowConfigurationInDashboard(settings)) {
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

  private fun hasTypeWithOppositeExclusion(selectedTypeId: String): Boolean {
    val manager = RunDashboardManagerProxy.getInstance(project)
    val newExcluded = manager.isNewExcluded(selectedTypeId)

    return manager.types.any { typeId ->
      typeId != selectedTypeId && manager.isNewExcluded(typeId) != newExcluded
    }
  }

  fun getTreeExpander(): TreeExpander? = if (hasFolders) treeExpander else null

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER] = true
    sink[PlatformDataKeys.TREE_EXPANDER] = getTreeExpander()
  }
}