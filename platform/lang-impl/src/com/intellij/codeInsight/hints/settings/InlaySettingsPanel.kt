// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.intention.impl.config.ActionUsagePanel
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.tree.TreeUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class InlaySettingsPanel(val project: Project): JPanel(BorderLayout()) {

  private val tree: CheckboxTree
  private val rightPanel: JPanel = JPanel(MigLayout("wrap, insets 0 10 0 0, gapy 20"))

  init {
    val models = InlaySettingsProvider.EP.getExtensions().flatMap { provider ->
      provider.getSupportedLanguages(project).flatMap { provider.createModels(project, it) }
    }
    val groups = models.groupBy { it.groupId }.toSortedMap(Comparator.comparing { sortedGroups.indexOf(it) })

    val root = CheckedTreeNode()
    val lastSelected = InlayHintsSettings.instance().getLastViewedProviderId()
    var nodeToSelect: CheckedTreeNode? = null
    for (group in groups) {
      val groupName = ApplicationBundle.message("settings.hints.group." + group.key)
      val groupNode = CheckedTreeNode(groupName)
      root.add(groupNode)
      for (lang in group.value.groupBy { it.language }) {
        val firstModel = lang.value.first()
        val langNode: CheckedTreeNode
        val startFrom: Int
        if ((lang.value.size == 1 || groupName == firstModel.name) && OTHER_GROUP != group.key) {
          nodeToSelect = addModelNode(firstModel, groupNode, lastSelected, nodeToSelect)
          firstModel.isMergedNode = true
          langNode = groupNode.firstChild as CheckedTreeNode
          startFrom = 1
        }
        else {
          langNode = CheckedTreeNode(lang.key)
          groupNode.add(langNode)
          startFrom = 0
        }

        for (it in startFrom until lang.value.size) {
          nodeToSelect = addModelNode(lang.value[it], langNode, lastSelected, nodeToSelect)
        }
      }
    }

    tree = CheckboxTree(object : CheckboxTree.CheckboxTreeCellRenderer(true, false) {
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
          is InlayProviderSettingsModel -> textRenderer.append(
            if ((value.parent as DefaultMutableTreeNode).userObject is String) item.language.displayName else item.name)
          is ImmediateConfigurable.Case -> textRenderer.appendHTML(item.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
      }
    }, root, CheckboxTreeBase.CheckPolicy(false, true, false, false))
    tree.addTreeSelectionListener(
      TreeSelectionListener { updateRightPanel(it?.newLeadSelectionPath?.lastPathComponent as? CheckedTreeNode) })
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
                           parent: CheckedTreeNode,
                           lastId: String?,
                           selected: CheckedTreeNode?): CheckedTreeNode? {
    var nodeToSelect: CheckedTreeNode? = selected
    model.onChangeListener = object : ChangeListener {
      override fun settingsChanged() {

      }
    }
    val node = CheckedTreeNode(model)
    parent.add(node)
    model.cases.forEach {
      val caseNode = object: CheckedTreeNode(it) {
        override fun setChecked(checked: Boolean) {
          super.setChecked(checked)
          it.value = checked
        }
      }
      caseNode.isChecked = it.value
      node.add(caseNode)
      if (nodeToSelect == null && getProviderId(caseNode) == lastId) {
        nodeToSelect = caseNode
      }
    }
    return if (nodeToSelect == null && getProviderId(node) == lastId) node else nodeToSelect
  }

  private fun updateRightPanel(treeNode: CheckedTreeNode?) {
    rightPanel.removeAll()
    when (val item = treeNode?.userObject) {
      is InlayProviderSettingsModel -> {
        if (treeNode.isLeaf) {
          addDescription(item.description)
        }
        item.component.border = JBUI.Borders.empty()
        rightPanel.add(item.component)
        if (treeNode.isLeaf) {
          addPreview(item.getCasePreview(null) ?: item.previewText, item.language)
        }
      }
      is ImmediateConfigurable.Case -> {
        val parent = treeNode.parent as CheckedTreeNode
        val model = parent.userObject as InlayProviderSettingsModel
        addDescription(model.getCaseDescription(item))
        val preview = model.getCasePreview(item)
        addPreview(preview, model.language)
      }
    }
    if (treeNode != null) {
      InlayHintsSettings.instance().saveLastViewedProviderId(getProviderId(treeNode))
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun addPreview(previewText: String?, language: Language) {
    if (previewText != null) {
      val usagePanel = ActionUsagePanel()
      usagePanel.editor.settings.isLineNumbersShown = false
      usagePanel.reset(previewText, language.associatedFileType)
      rightPanel.add(usagePanel, "growx")
    }
  }

  private fun addDescription(@Nls s: String?) {
    val htmlLabel = SwingHelper.createHtmlLabel(StringUtil.notNullize(s), null, null)
    rightPanel.add(htmlLabel, "growy, width 200:300:300")
  }

  private fun getProviderId(treeNode: CheckedTreeNode): String {
    when (val item = treeNode.userObject) {
      is InlayProviderSettingsModel -> {
        return item.language.id + "." + item.id
      }
      is ImmediateConfigurable.Case -> {
        val model = (treeNode.parent as CheckedTreeNode).userObject as InlayProviderSettingsModel
        return model.language.id + "." + model.id + "." + item.id
      }
    }
    return ""
  }

  fun reset() {
    reset(tree.model.root as CheckedTreeNode, InlayHintsSettings.instance())
  }

  private fun reset(node: CheckedTreeNode, settings: InlayHintsSettings) {
    when (val item = node.userObject) {
      is InlayProviderSettingsModel -> {
        val enabled = isModelEnabled(item, settings)
        if (enabled != node.isChecked) {
          node.isChecked = enabled
          refreshNode(node)
        }
        item.reset()
      }
      is ImmediateConfigurable.Case -> {
        if (item.value != node.isChecked) {
          node.isChecked = item.value
          refreshNode(node)
        }
      }
      is Language -> {
        if (node.isChecked != settings.hintsEnabled(item)) {
          node.isChecked = settings.hintsEnabled(item)
          refreshNode(tree.model.root as CheckedTreeNode)
        }
      }
    }
    node.children().toList().forEach { reset(it as CheckedTreeNode, settings) }
  }

  private fun isModelEnabled(model: InlayProviderSettingsModel, settings: InlayHintsSettings): Boolean {
    return model.isEnabled && (!model.isMergedNode || settings.hintsEnabled(model.language))
  }

  private fun refreshNode(node: CheckedTreeNode) {
    val treeModel = tree.model as DefaultTreeModel
    treeModel.nodeChanged(node)
    treeModel.nodeChanged(node.parent)
    if (node.parent != null) {
      treeModel.nodeChanged(node.parent.parent)
    }
  }

  fun apply() {
    apply(tree.model.root as CheckedTreeNode, InlayHintsSettings.instance())
  }

  private fun apply(node: CheckedTreeNode, settings: InlayHintsSettings) {
    when (val item = node.userObject) {
      is InlayProviderSettingsModel -> {
        item.isEnabled = node.isChecked
        item.apply()
        if (item.isMergedNode) {
          enableHintsForLanguage(item.language, settings, node)
        }
      }
      is ImmediateConfigurable.Case -> {
        item.value = node.isChecked
      }
      is Language -> {
        enableHintsForLanguage(item, settings, node)
      }
    }
    node.children().toList().forEach { apply(it as CheckedTreeNode, settings) }
  }

  private fun enableHintsForLanguage(language: Language, settings: InlayHintsSettings, node: CheckedTreeNode) {
    if (node.isChecked && !settings.hintsEnabled(language)) {
      settings.setHintsEnabledForLanguage(language, true)
    }
  }

  fun isModified(): Boolean {
    return isModified(tree.model.root as CheckedTreeNode, InlayHintsSettings.instance())
  }

  private fun isModified(node: CheckedTreeNode, settings: InlayHintsSettings): Boolean {
    when (val item = node.userObject) {
      is InlayProviderSettingsModel -> {
        if ((node.isChecked != isModelEnabled(item, settings)) || item.isModified())
          return true
      }
      is Language -> {
        if (settings.hintsEnabled(item) != node.isChecked)
          return true
      }
    }
    return node.children().toList().any { isModified(it as CheckedTreeNode, settings) }
  }
}