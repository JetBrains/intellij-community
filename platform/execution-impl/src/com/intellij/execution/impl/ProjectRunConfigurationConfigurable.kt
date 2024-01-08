// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

open class ProjectRunConfigurationConfigurable(project: Project) : RunConfigurable(project) {
  override fun createLeftPanel(): JComponent {

    if (project.isDefault) {
      return ScrollPaneFactory.createScrollPane(tree)
    }

    val removeAction = MyRemoveAction()
    toolbarDecorator = ToolbarDecorator.createDecorator(tree)
      .setToolbarPosition(ActionToolbarPosition.TOP)
      .setPanelBorder(JBUI.Borders.empty())
      .setScrollPaneBorder(JBUI.Borders.empty())
      .setAddAction(toolbarAddAction).setAddActionName(ExecutionBundle.message("add.new.run.configuration.action2.name"))
      .setRemoveAction(removeAction).setRemoveActionUpdater(removeAction)
      .setRemoveActionName(ExecutionBundle.message("remove.run.configuration.action.name"))

      .addExtraAction(MyCopyAction())
      .addExtraAction(MySaveAction())
      .addExtraAction(MyCreateFolderAction())
      .addExtraAction(MySortFolderAction())
      .setMinimumSize(JBDimension(200, 200))
      .setButtonComparator(ExecutionBundle.message("add.new.run.configuration.action2.name"),
                           ExecutionBundle.message("remove.run.configuration.action.name"),
                           ExecutionBundle.message("copy.configuration.action.name"),
                           ExecutionBundle.message("action.name.save.configuration"),
                           ExecutionBundle.message("run.configuration.edit.default.configuration.settings.text"),
                           ExecutionBundle.message("move.up.action.name"),
                           ExecutionBundle.message("move.down.action.name"),
                           ExecutionBundle.message("run.configuration.create.folder.text"))
      .setForcedDnD()
    val panel = JPanel(BorderLayout())
    panel.background = JBColor.background()
    panel.add(toolbarDecorator!!.createPanel(), BorderLayout.CENTER)
    val actionLink = ActionLink(ExecutionBundle.message("edit.configuration.templates")) { showTemplatesDialog(project, selectedConfigurationType) }
    actionLink.border = JBUI.Borders.empty(10)
    actionLink.background = JBColor.background()
    panel.add(actionLink, BorderLayout.SOUTH)
    initTree()
    return panel
  }

  override fun typeOrFactorySelected(userObject: Any) {
    drawPressAddButtonMessage(userObject as ConfigurationType)
  }

  override fun addRunConfigurationsToModel(model: DefaultMutableTreeNode) {
    for ((type, folderMap) in runManager.getConfigurationsGroupedByTypeAndFolder(true)) {
      val typeNode = DefaultMutableTreeNode(type)
      model.add(typeNode)
      for ((folder, configurations) in folderMap.entries) {
        val node: DefaultMutableTreeNode
        if (folder == null) {
          node = typeNode
        }
        else {
          node = DefaultMutableTreeNode(folder)
          typeNode.add(node)
        }

        for (it in configurations) {
          node.add(DefaultMutableTreeNode(it))
        }
      }
    }
  }

  override fun createTipPanelAboutAddingNewRunConfiguration(configurationType: ConfigurationType?): JComponent {
    val messagePanel = JBPanelWithEmptyText()
    messagePanel.emptyText.appendLine(ExecutionBundle.message("status.text.add.new.run.configuration"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
      if (configurationType == null) {
        toolbarAddAction.showAddPopup(true, it.source as MouseEvent)
      }
      else if (configurationType.configurationFactories.isNotEmpty()) createNewConfiguration(configurationType.configurationFactories[0])
    }.appendLine(ExecutionBundle.message("status.text.or.select.run.configuration.to.edit"), SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
    return messagePanel
  }
}