// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.settings.language.NewInlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.language.ParameterInlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.language.createEditor
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class InlaySettingsPanel(val project: Project): JPanel(BorderLayout()) {

  private val tree: CheckboxTree
  private val rightPanel: JPanel = JPanel(MigLayout("wrap, insets 0 10 0 0"))

  init {
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
          is ImmediateConfigurable.Case -> textRenderer.append(item.name)
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
    model.cases.forEach {
      val caseNode = object: CheckedTreeNode(it) {
        override fun setChecked(checked: Boolean) {
          super.setChecked(checked)
          it.value = checked
        }
      }
      caseNode.isChecked = it.value
      node.add(caseNode)
    }
    return node
  }

  private fun updateRightPanel(treeNode: CheckedTreeNode?) {
    rightPanel.removeAll()
    when (val item = treeNode?.userObject) {
      is InlayProviderSettingsModel -> {
        InlayHintsSettings.instance().saveLastViewedProviderId(getProviderId(treeNode))

        addDescription(item.description)
        item.component.border = JBUI.Borders.empty()
        rightPanel.add(item.component)
        addPreview(treeNode, item.previewText)
      }
      is ImmediateConfigurable.Case -> {
        addDescription(item.extendedDescription)
        val parent = treeNode.parent as CheckedTreeNode
        val preview = (parent.userObject as InlayProviderSettingsModel).getCasePreview(item)
        addPreview(parent, preview)
      }
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun addPreview(treeNode: CheckedTreeNode, previewText: String?) {
    if (previewText != null) {
      val editor = createEditor(getModelLanguage(treeNode), project) {}
      editor.text = previewText
      rightPanel.add(editor, "gaptop 10")
    }
  }

  private fun addDescription(@Nls s: String?) {
    val htmlBody = UIUtil.toHtml(StringUtil.notNullize(s))
    rightPanel.add(JLabel(htmlBody), "growy, width 200:300:300")
  }

  private fun getProviderId(treeNode: CheckedTreeNode): String {
    val language = getModelLanguage(treeNode)
    return language.id + "." + (treeNode.userObject as InlayProviderSettingsModel).id
  }

  private fun getModelLanguage(treeNode: CheckedTreeNode): Language {
    return (treeNode.parent as DefaultMutableTreeNode).userObject as Language
  }

  fun reset() {
    reset(tree.model.root as CheckedTreeNode)
  }

  private fun reset(node: CheckedTreeNode) {
    when (node.userObject) {
      is InlayProviderSettingsModel -> {
        val model = node.userObject as InlayProviderSettingsModel
        if (model.isEnabled != node.isChecked) {
          node.isChecked = model.isEnabled
          (tree.model as DefaultTreeModel).nodeChanged(node)
        }
        model.reset()
      }
      is ImmediateConfigurable.Case -> {
        val case = node.userObject as ImmediateConfigurable.Case
        if (case.value != node.isChecked) {
          node.isChecked = case.value
          (tree.model as DefaultTreeModel).nodeChanged(node)
        }
      }
    }
    node.children().toList().forEach { reset(it as CheckedTreeNode) }
  }

  fun apply() {
    apply(tree.model.root as CheckedTreeNode)
  }

  private fun apply(node: CheckedTreeNode) {
    when (node.userObject) {
      is InlayProviderSettingsModel -> {
        val model = node.userObject as InlayProviderSettingsModel
        model.isEnabled = node.isChecked
        model.apply()
      }
      is ImmediateConfigurable.Case -> {
        (node.userObject as ImmediateConfigurable.Case).value = node.isChecked
      }
    }
    node.children().toList().forEach { apply(it as CheckedTreeNode) }
  }

  fun isModified(): Boolean {
    return isModified(tree.model.root as CheckedTreeNode)
  }

  private fun isModified(node: CheckedTreeNode): Boolean {
    when (node.userObject) {
      is InlayProviderSettingsModel -> {
        val model = node.userObject as InlayProviderSettingsModel
        if ((node.isChecked != model.isEnabled) || model.isModified()) return true
      }
    }
    return node.children().toList().any { isModified(it as CheckedTreeNode) }
  }
}