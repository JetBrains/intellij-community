// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.VirtualConfigurationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

internal fun showTemplatesDialog(project: Project, selectedConfigurationType: ConfigurationType?) {
  RunConfigurationTemplatesDialog(project, selectedConfigurationType).show()
}

class RunConfigurationTemplatesDialog(project: Project, selectedConfigurationType: ConfigurationType?) :
  SingleConfigurableEditor(project, RunConfigurationTemplatesConfigurable(project, selectedConfigurationType)) {

  init {
    (configurable as RunConfigurationTemplatesConfigurable).postInit(disposable)
  }

  override fun getStyle(): DialogStyle {
    return DialogStyle.COMPACT
  }
}

class RunConfigurationTemplatesConfigurable(project: Project, private val configurationType: ConfigurationType?) : RunConfigurable(project) {

  internal fun postInit(disposable: Disposable) {
    initTreeSelectionListener(disposable)
    selectTypeNode(configurationType)
  }

  private fun selectTypeNode(configurationType: ConfigurationType?) {
    configurationType?.let {
      val node = TreeUtil.findNodeWithObject(it, tree.model, root) ?: return
      expandTemplatesNode(node as DefaultMutableTreeNode)
    }
  }

  private fun expandTemplatesNode(templatesNode: DefaultMutableTreeNode) {
    val path = TreeUtil.getPath(root, templatesNode)
    tree.expandPath(path)
    TreeUtil.selectInTree(templatesNode, true, tree)
    tree.scrollPathToVisible(path)
  }

  override fun addRunConfigurationsToModel(model: DefaultMutableTreeNode) {
    // add templates
    for (type in ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.filter { it !is VirtualConfigurationType }) {
      val configurationFactories = type.configurationFactories
      val typeNode = DefaultMutableTreeNode(type)
      root.add(typeNode)
      if (configurationFactories.size != 1) {
        for (factory in configurationFactories) {
          typeNode.add(DefaultMutableTreeNode(factory))
        }
      }
    }
  }

  override fun apply() {
    applyTemplates()
  }

  override fun isModified(): Boolean {
    return isConfigurableModified()
  }

  override fun createComponent(): JComponent {
    val component = super.createComponent()
    val label = JLabel(ExecutionBundle.message("templates.disclaimer"))
    label.border = JBUI.Borders.empty(10)
    val panel = JPanel(BorderLayout())
    panel.add(label, BorderLayout.WEST)
    panel.border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    panel.background = EditorColorsManager.getInstance().globalScheme.getColor(HintUtil.PROMOTION_PANE_KEY)
    component!!.add(panel, BorderLayout.NORTH)
    return component
  }

  override fun createLeftPanel(): JComponent {
    val leftPanel = super.createLeftPanel()
    leftPanel.minimumSize = Dimension(250, 100)
    return leftPanel
  }

  override fun getDisplayName(): String {
    return ExecutionBundle.message("configurable.name.run.debug.configuration.templates")
  }
}