// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.EmptyIcon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

internal class RunConfigurableTreeRenderer(private val runManager: RunManagerImpl) : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
    if (value !is DefaultMutableTreeNode) {
      return
    }

    val userObject = value.userObject
    var isShared: Boolean? = null
    val name = getUserObjectName(userObject)
    val isDumb = DumbService.isDumb(runManager.project)
    when {
      userObject is ConfigurationType -> {
        val simpleTextAttributes = when {
          (value.parent as DefaultMutableTreeNode).isRoot -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
          isDumb && !ConfigurationTypeUtil.isEditableInDumbMode(userObject) -> SimpleTextAttributes.GRAYED_ATTRIBUTES
          else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        append(name, simpleTextAttributes)
        icon = userObject.icon
      }
      userObject is String -> {
        // folder
        append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        icon = AllIcons.Nodes.Folder
      }
      userObject is ConfigurationFactory -> {
        append(name,
               if (isDumb && !userObject.isEditableInDumbMode) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
        icon = userObject.icon
      }
      else -> {
        var configuration: RunnerAndConfigurationSettings? = null
        if (userObject is SingleConfigurationConfigurable<*>) {
          val configurationSettings: RunnerAndConfigurationSettings = userObject.settings
          configuration = configurationSettings
          isShared = userObject.isStoredInFile
          icon = IconManager.getInstance().createDeferredIcon(ProgramRunnerUtil.getConfigurationIcon(configurationSettings, false), userObject) {
            return@createDeferredIcon ProgramRunnerUtil.getConfigurationIcon(configurationSettings, !it.isValid)
          }
        }
        else if (userObject is RunnerAndConfigurationSettings) {
          isShared = userObject.isShared
          icon = runManager.getConfigurationIcon(userObject)
          configuration = userObject
        }
        if (configuration != null) {
          val simpleTextAttributes = when {
            configuration.isTemporary -> SimpleTextAttributes.GRAY_ATTRIBUTES
            isDumb && !ConfigurationTypeUtil.isEditableInDumbMode(configuration) -> SimpleTextAttributes.GRAY_ATTRIBUTES
            else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
          }
          append(name, simpleTextAttributes)
        }
      }
    }

    if (isShared == null) {
      iconTextGap = 2
    }
    else {
      icon = LayeredIcon(icon, if (isShared) AllIcons.Nodes.Shared else EmptyIcon.ICON_16)
      iconTextGap = 0
    }
  }
}