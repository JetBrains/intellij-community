// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.settings.language.NewInlayProviderSettingsModel
import com.intellij.codeInsight.hints.withSettings
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

class InlaySettingsPanel(val project: Project): JPanel(BorderLayout()) {

  private val tree: CheckboxTree
  private val rightPanel: JPanel = JPanel()

  init {
    rightPanel.layout = BoxLayout(rightPanel, BoxLayout.Y_AXIS)

    val settings = InlayHintsSettings.instance()
    val root = CheckedTreeNode()
    for (group in InlayHintsProviderExtension.findProviders().groupBy { it.provider.groupId }) {
      val groupNode = CheckedTreeNode(ApplicationBundle.message("settings.hints.group." + group.key))
      root.add(groupNode)
      for (lang in group.value.groupBy { it.language }) {
        val langNode = CheckedTreeNode(lang.key)
        groupNode.add(langNode)

        lang.value.forEach {
          val withSettings = it.provider.withSettings(lang.key, settings)
          val model = NewInlayProviderSettingsModel(withSettings, settings)
          model.onChangeListener = object : ChangeListener {
            override fun settingsChanged() {

            }
          }
          langNode.add(CheckedTreeNode(model))
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
    TreeUtil.expand(tree, 1)
    tree.addTreeSelectionListener(TreeSelectionListener { updateRightPanel(it?.newLeadSelectionPath?.lastPathComponent as? CheckedTreeNode) })

    val splitter = JBSplitter(false, 0.3f)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree)
    splitter.secondComponent = rightPanel
    add(splitter, BorderLayout.CENTER)
  }

  private fun updateRightPanel(treeNode: CheckedTreeNode?) {
    rightPanel.removeAll()
    when (val item = treeNode?.userObject) {
      is InlayProviderSettingsModel -> {
        rightPanel.add(JLabel(item.name))
        rightPanel.add(item.component)
      }
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }
}