// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.settings.language.CaseListPanel
import com.intellij.codeInsight.hints.settings.language.NewInlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.language.ParameterInlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.language.createEditor
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class InlaySettingsPanel(val project: Project): JPanel(BorderLayout()) {

  private val tree: CheckboxTree
  private val rightPanel: JPanel = JPanel()

  init {
    rightPanel.layout = BoxLayout(rightPanel, BoxLayout.Y_AXIS)

    val settings = InlayHintsSettings.instance()
    val root = CheckedTreeNode()
    var nodeToSelect: CheckedTreeNode? = null
    for (group in InlayHintsProviderExtension.findProviders().groupBy { it.provider.groupId }) {
      val groupNode = CheckedTreeNode(ApplicationBundle.message("settings.hints.group." + group.key))
      root.add(groupNode)
      for (lang in group.value.groupBy { it.language }) {
        val langNode = CheckedTreeNode(lang.key)
        groupNode.add(langNode)

        if (group.key == CODE_VISION_GROUP) {
          val parameterHintsProvider = InlayParameterHintsExtension.forLanguage(lang.key)
          if (parameterHintsProvider != null) {
            val node = addModelNode(ParameterInlayProviderSettingsModel(parameterHintsProvider, lang.key), langNode)
            if (nodeToSelect == null && getProviderId(node) == settings.getLastViewedProviderId()) {
              nodeToSelect = node
            }
          }
        }

        lang.value.forEach {
          val withSettings = it.provider.withSettings(lang.key, settings)
          val node = addModelNode(NewInlayProviderSettingsModel(withSettings, settings), langNode)
          if (nodeToSelect == null && getProviderId(node) == settings.getLastViewedProviderId()) {
            nodeToSelect = node
          }
        }
      }
    }

    tree = CheckboxTree(object : CheckboxTree.CheckboxTreeCellRenderer() {
      override fun customizeRenderer(tree: JTree?,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
        if (value !is DefaultMutableTreeNode) return

        when (val item = value.userObject) {
          is String -> textRenderer.append(item)
          is Language -> textRenderer.append(item.displayName)
          is InlayProviderSettingsModel -> textRenderer.append(item.name)
        }
      }
    }, root)
    tree.addTreeSelectionListener(TreeSelectionListener { updateRightPanel(it?.newLeadSelectionPath?.lastPathComponent as? CheckedTreeNode) })
    if (nodeToSelect == null) {
      TreeUtil.expand(tree, 1)
    }
    else {
      TreeUtil.selectNode(tree, nodeToSelect)
    }

    val splitter = JBSplitter(false, 0.3f)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree)
    splitter.secondComponent = rightPanel
    add(splitter, BorderLayout.CENTER)
  }

  private fun addModelNode(model: InlayProviderSettingsModel,
                           langNode: CheckedTreeNode): CheckedTreeNode {
    model.onChangeListener = object : ChangeListener {
      override fun settingsChanged() {

      }
    }
    val node = CheckedTreeNode(model)
    node.isChecked = model.isEnabled
    langNode.add(node)
    return node
  }

  private fun updateRightPanel(treeNode: CheckedTreeNode?) {
    rightPanel.removeAll()
    when (val item = treeNode?.userObject) {
      is InlayProviderSettingsModel -> {
        InlayHintsSettings.instance().saveLastViewedProviderId(getProviderId(treeNode))

        rightPanel.add(JLabel(item.name))
        rightPanel.add(CaseListPanel(item.cases, item.onChangeListener!!))
        rightPanel.add(item.component)
        if (item.previewText != null) {
          val editor = createEditor(getModelLanguage(treeNode), project) {}
          editor.text = item.previewText!!
          rightPanel.add(editor)
        }
      }
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun getProviderId(treeNode: CheckedTreeNode): String {
    val language = getModelLanguage(treeNode)
    return language.id + "." + (treeNode.userObject as InlayProviderSettingsModel).id
  }

  private fun getModelLanguage(treeNode: CheckedTreeNode): Language {
    return (treeNode.parent as DefaultMutableTreeNode).userObject as Language
  }

  fun reset() {
    reset(tree.model.root as DefaultMutableTreeNode)
  }

  private fun reset(node: DefaultMutableTreeNode) {
    if (node.userObject is InlayProviderSettingsModel) {
      val model = node.userObject as InlayProviderSettingsModel
      if (model.isEnabled != (node as CheckedTreeNode).isChecked) {
        node.isChecked = model.isEnabled
        (tree.model as DefaultTreeModel).nodeChanged(node)
      }
      model.reset()
    }
    node.children().toList().forEach { reset(it as DefaultMutableTreeNode) }
  }

  fun apply() {
    apply(tree.model.root as DefaultMutableTreeNode)
  }

  private fun apply(node: DefaultMutableTreeNode) {
    if (node.userObject is InlayProviderSettingsModel) {
      val model = node.userObject as InlayProviderSettingsModel
      model.isEnabled = (node as CheckedTreeNode).isChecked
      model.apply()
    }
    node.children().toList().forEach { apply(it as DefaultMutableTreeNode) }
  }

  fun isModified(): Boolean {
    return isModified(tree.model.root as DefaultMutableTreeNode)
  }

  private fun isModified(node: DefaultMutableTreeNode): Boolean {
    if (node.userObject is InlayProviderSettingsModel) {
      val model = node.userObject as InlayProviderSettingsModel
      if (((node as CheckedTreeNode).isChecked != model.isEnabled) || model.isModified()) return true
    }
    return node.children().toList().any { isModified(it as DefaultMutableTreeNode) }
  }
}