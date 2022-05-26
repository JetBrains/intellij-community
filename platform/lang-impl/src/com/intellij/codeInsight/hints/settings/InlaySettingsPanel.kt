// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.settings.language.createEditor
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.tree.TreeUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.util.concurrent.Callable
import java.util.function.Predicate
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

class InlaySettingsPanel(val project: Project): JPanel(BorderLayout()) {

  val tree: CheckboxTree
  private val rightPanel: JPanel = JPanel(MigLayout("wrap, insets 0 10 0 0, gapy 20, fillx"))
  private val groups: Map<InlayGroup, List<InlayProviderSettingsModel>>
  private var currentEditor: Editor? = null

  init {
    val models = InlaySettingsProvider.EP.getExtensions().flatMap { provider ->
      provider.getSupportedLanguages(project).flatMap { provider.createModels(project, it) }
    }
    groups = models.groupBy { it.group }.toSortedMap()

    val root = CheckedTreeNode()
    val lastSelected = InlayHintsSettings.instance().getLastViewedProviderId()
    var nodeToSelect: CheckedTreeNode? = null
    for (group in groups) {
      val groupNode = CheckedTreeNode(group.key)
      root.add(groupNode)
      val primaryLanguages = IdeLanguageCustomization.getInstance().primaryIdeLanguages
      val sortedMap = group.value.groupBy { it.language }.toSortedMap(
        Comparator { o1, o2 -> val primary = compareValues(primaryLanguages.contains(o2), primaryLanguages.contains(o1))
          if (primary != 0) primary
          else compareValues(o1.displayName, o2.displayName)
        })
      for (lang in sortedMap) {
        val firstModel = lang.value.first()
        val langNode: CheckedTreeNode
        val startFrom: Int
        if ((lang.value.size == 1 || group.key.toString() == firstModel.name && firstModel.language == sortedMap.firstKey()) &&
            InlayGroup.OTHER_GROUP != group.key) {
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

    tree = object: CheckboxTree(object : CheckboxTreeCellRenderer(true, true) {
      override fun customizeRenderer(tree: JTree?,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
        if (value !is DefaultMutableTreeNode) return

        val name = getName(value, value.parent as? DefaultMutableTreeNode)
        textRenderer.appendHTML(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }, root, CheckPolicy(true, true, true, false)) {
      override fun installSpeedSearch() {
        TreeSpeedSearch(this, Convertor { getName(it.lastPathComponent as DefaultMutableTreeNode,
                                                  it.parentPath?.lastPathComponent as DefaultMutableTreeNode?) }, true)
      }
    }
    tree.addTreeSelectionListener(
      TreeSelectionListener { updateRightPanel(it?.newLeadSelectionPath?.lastPathComponent as? CheckedTreeNode) })
    if (nodeToSelect == null) {
      TreeUtil.expand(tree, 1)
    }
    else {
      TreeUtil.selectNode(tree, nodeToSelect)
    }

    val splitter = JBSplitter(false, "inlay.settings.proportion.key", 0.45f)
    splitter.setHonorComponentsMinimumSize(false)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    splitter.secondComponent = rightPanel
    add(splitter, BorderLayout.CENTER)
  }

  @Nls
  private fun getName(node: DefaultMutableTreeNode?, parent: DefaultMutableTreeNode?): String {
    when (val item = node?.userObject) {
      is InlayGroup -> return item.toString()
      is Language -> return item.displayName
      is InlayProviderSettingsModel -> return if (parent?.userObject is InlayGroup) item.language.displayName else item.name
      is ImmediateConfigurable.Case -> return item.name
    }
    return ""
  }

  private fun addModelNode(model: InlayProviderSettingsModel,
                           parent: CheckedTreeNode,
                           lastId: String?,
                           selected: CheckedTreeNode?): CheckedTreeNode? {
    var nodeToSelect: CheckedTreeNode? = selected
    model.onChangeListener = object : ChangeListener {
      override fun settingsChanged() {
        currentEditor?.let { updateHints(it, model) }
      }
    }
    val node = object: CheckedTreeNode(model) {
      override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        model.isEnabled = checked
        model.onChangeListener?.settingsChanged()
      }
    }
    parent.add(node)
    model.cases.forEach {
      val caseNode = object: CheckedTreeNode(it) {
        override fun setChecked(checked: Boolean) {
          super.setChecked(checked)
          it.value = checked
          model.onChangeListener?.settingsChanged()
        }
      }
      node.add(caseNode)
      if (nodeToSelect == null && getProviderId(caseNode) == lastId) {
        nodeToSelect = caseNode
      }
    }
    return if (nodeToSelect == null && getProviderId(node) == lastId) node else nodeToSelect
  }

  private fun updateRightPanel(treeNode: CheckedTreeNode?) {
    rightPanel.removeAll()
    currentEditor = null
    when (val item = treeNode?.userObject) {
      is InlayProviderSettingsModel -> {
        if (treeNode.isLeaf) {
          addDescription(item.description)
        }
        item.component.border = JBUI.Borders.empty()
        rightPanel.add(item.component)
        if (treeNode.isLeaf) {
          addPreview(item.getCasePreview(null) ?: item.previewText, item)
        }
      }
      is ImmediateConfigurable.Case -> {
        val parent = treeNode.parent as CheckedTreeNode
        val model = parent.userObject as InlayProviderSettingsModel
        addDescription(model.getCaseDescription(item))
        val preview = model.getCasePreview(item)
        addPreview(preview, model)
      }
    }
    if (treeNode != null) {
      InlayHintsSettings.instance().saveLastViewedProviderId(getProviderId(treeNode))
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun addPreview(previewText: String?, model: InlayProviderSettingsModel) {
    if (previewText != null) {
      val editorTextField = createEditor(model.language, project) { editor ->
        currentEditor = editor
        updateHints(editor, model)
      }
      editorTextField.text = previewText
      editorTextField.addSettingsProvider {
        it.setBorder(JBUI.Borders.empty(10))
        it.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
        it.settings.apply {
          isLineNumbersShown = false
          isCaretRowShown = false
          isRightMarginShown = false
        }
      }
      rightPanel.add(ScrollPaneFactory.createScrollPane(editorTextField), "growx")
    }
  }

  private fun updateHints(editor: Editor, model: InlayProviderSettingsModel) {
    val fileType = model.language.associatedFileType ?: PlainTextFileType.INSTANCE
    ReadAction.nonBlocking(Callable {
      model.createFile(project, fileType, editor.document)
    })
      .finishOnUiThread(ModalityState.defaultModalityState()) { psiFile ->
        ApplicationManager.getApplication().runWriteAction {
          model.collectAndApply(editor, psiFile)
        }
      }
      .inSmartMode(project)
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun addDescription(@Nls s: String?) {
    val htmlLabel = SwingHelper.createHtmlLabel(StringUtil.notNullize(s), null, null)
    rightPanel.add(htmlLabel, "growy")
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
        item.reset()
        resetNode(node, isModelEnabled(item, settings))
      }
      is ImmediateConfigurable.Case -> {
        resetNode(node, isCaseEnabled(item, node.parent, settings))
      }
      is Language -> {
        resetNode(node, isLanguageEnabled(settings, item))
      }
    }
    node.children().toList().forEach { reset(it as CheckedTreeNode, settings) }
    when (val item = node.userObject) {
      is InlayGroup -> {
        node.isChecked = groups[item]?.any { it.isEnabled } == true
      }
    }
  }

  private fun isCaseEnabled(item: ImmediateConfigurable.Case,
                            parent: TreeNode,
                            settings: InlayHintsSettings) = item.value && ((parent as CheckedTreeNode).userObject as InlayProviderSettingsModel).isEnabled && settings.hintsEnabledGlobally()

  private fun isModelEnabled(model: InlayProviderSettingsModel, settings: InlayHintsSettings): Boolean {
    return model.isEnabled && (!model.isMergedNode || settings.hintsEnabled(model.language)) && settings.hintsEnabledGlobally()
  }

  private fun resetNode(node: CheckedTreeNode, value: Boolean) {
    if (node.isChecked == value) return
    node.isChecked = value
    val treeModel = tree.model as DefaultTreeModel
    treeModel.nodeChanged(node)
    treeModel.nodeChanged(node.parent)
    if (node.parent != null) {
      treeModel.nodeChanged(node.parent.parent)
    }
  }

  fun apply() {
    apply(tree.model.root as CheckedTreeNode, InlayHintsSettings.instance())
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    InlayHintsPassFactory.forceHintsUpdateOnNextPass()
  }

  private fun apply(node: CheckedTreeNode, settings: InlayHintsSettings) {
    node.children().toList().forEach { apply(it as CheckedTreeNode, settings) }
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
        if (node.isChecked) {
          settings.setEnabledGlobally(true)
        }
      }
      is Language -> {
        enableHintsForLanguage(item, settings, node)
      }
    }
  }

  private fun enableHintsForLanguage(language: Language, settings: InlayHintsSettings, node: CheckedTreeNode) {
    if (node.isChecked && !isLanguageEnabled(settings, language)) {
      settings.setHintsEnabledForLanguage(language, true)
    }
  }

  fun isModified(): Boolean {
    return isModified(tree.model.root as CheckedTreeNode, InlayHintsSettings.instance())
  }

  private fun isModified(node: CheckedTreeNode, settings: InlayHintsSettings): Boolean {
    when (val item = node.userObject) {
      is InlayProviderSettingsModel -> {
        if (item.isModified() || (node.isChecked != isModelEnabled(item, settings)))
          return true
      }
      is ImmediateConfigurable.Case -> {
        if (node.isChecked != isCaseEnabled(item, node.parent, settings)) {
          return true
        }
      }
      is Language -> {
        if (isLanguageEnabled(settings, item) != node.isChecked)
          return true
      }
    }
    return node.children().toList().any { isModified(it as CheckedTreeNode, settings) }
  }

  private fun isLanguageEnabled(settings: InlayHintsSettings, item: Language) = settings.hintsEnabled(item)

  fun enableSearch(option: String?): Runnable? {
    if (option == null) return null
    return Runnable {
      val treeNode = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode, Condition {
        getName(it, it.parent as DefaultMutableTreeNode?).lowercase().startsWith(option.lowercase())
      })
      if (treeNode != null) {
        TreeUtil.selectNode(tree, treeNode)
      }
    }
  }

  fun selectModel(language: Language, selector: Predicate<InlayProviderSettingsModel>?) {
    val languages = LanguageUtil.getBaseLanguages(language).toSet()
    val node = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
      if (selector == null) {
        language in languages
      }
      else {
        val model = it.userObject as? InlayProviderSettingsModel
        model != null && selector.test(model) && model.language in languages
      }
    }
    if (node != null) {
      TreeUtil.selectNode(tree, node)
    }
  }
}