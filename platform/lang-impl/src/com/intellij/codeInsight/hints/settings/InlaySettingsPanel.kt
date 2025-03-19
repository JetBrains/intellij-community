// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal

package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.codeInsight.hints.settings.language.createEditor
import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorTreeRendererContextProvider
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.tree.TreeUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
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

val CASE_KEY: Key<ImmediateConfigurable.Case> = Key.create("inlay.case.key")

class InlaySettingsPanel(val project: Project) : JPanel(BorderLayout()) {

  val tree: CheckboxTree
  private val rightPanel: JPanel = JPanel(MigLayout("wrap, insets 0 10 0 0, gapy 20, fillx"))
  private val groups: MutableMap<InlayGroup, List<InlayProviderSettingsModel>>
  private var currentEditor: Editor? = null

  companion object {
    @JvmField
    val PREVIEW_KEY: Key<Any> = Key.create("inlay.preview.key")

    fun getFileTypeForPreview(model: InlayProviderSettingsModel): LanguageFileType {
      return model.getCasePreviewLanguage(null)?.associatedFileType ?: PlainTextFileType.INSTANCE
    }
  }

  init {
    val models = InlaySettingsProvider.EP.getExtensions().flatMap { provider ->
      provider.getSupportedLanguages(project).flatMap { provider.createModels(project, it) }
    }
    groups = models.groupBy { it.group }.toSortedMap()
    val globalSettings = groups.keys.associateWith { InlayGroupSettingProvider.EP.findForGroup(it) }

    val root = CheckedTreeNode()
    val lastSelected = InlayHintsSettings.instance().getLastViewedProviderId()
    var nodeToSelect: CheckedTreeNode? = null

    // filling code vision settings
    if (Registry.`is`("editor.codeVision.new")) {
      groups.remove(InlayGroup.CODE_VISION_GROUP)
    }

    for (group in groups) {
      val groupNode = CheckedTreeNode(globalSettings[group.key] ?: group.key)
      root.add(groupNode)
      val primaryLanguages = IdeLanguageCustomization.getInstance().primaryIdeLanguages
      val sortedMap = group.value.groupBy { it.language }.toSortedMap(
        Comparator { o1, o2 ->
          val primary = compareValues(primaryLanguages.contains(o2), primaryLanguages.contains(o1))
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
        else if (lang.key == Language.ANY) {
          langNode = groupNode
          startFrom = 0
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

    tree = object : CheckboxTree(InlaySettingsTreeRenderer(), root, CheckPolicy(true, true, true, false)) {
      override fun installSpeedSearch() {
        TreeSpeedSearch.installOn(this, true) {
          getName(it.lastPathComponent as DefaultMutableTreeNode,
                  it.parentPath?.lastPathComponent as DefaultMutableTreeNode?)
        }
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
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                 ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    splitter.secondComponent = rightPanel
    add(splitter, BorderLayout.CENTER)
  }

  @Nls
  private fun getName(node: DefaultMutableTreeNode?, parent: DefaultMutableTreeNode?): String {
    when (val item = node?.userObject) {
      is InlayGroupSettingProvider -> return item.group.title()
      is InlayGroup -> return item.title()
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
        currentEditor?.let {
          val case = (it.getUserData(PREVIEW_KEY) as? CaseCheckedNode)?.userObject as? ImmediateConfigurable.Case
          updateHints(it, model, case)
        }
      }
    }
    val node = object : CheckedTreeNode(model) {
      override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        model.isEnabled = checked
        model.onChangeListener?.settingsChanged()
      }
    }
    parent.add(node)
    model.cases.forEach {
      val caseNode = CaseCheckedNode(it, { currentEditor }, model)
      node.add(caseNode)
      if (nodeToSelect == null && getProviderId(caseNode) == lastId) {
        nodeToSelect = caseNode
      }
    }
    return if (nodeToSelect == null && getProviderId(node) == lastId) node else nodeToSelect
  }

  private class CaseCheckedNode(
    private val case: ImmediateConfigurable.Case,
    private val editorProvider: () -> Editor?,
    private val model: InlayProviderSettingsModel
  ) : CheckedTreeNode(case) {
    override fun setChecked(checked: Boolean) {
      super.setChecked(checked)
      case.value = checked
      if (PREVIEW_KEY.get(editorProvider()) == this) {
        model.onChangeListener?.settingsChanged()
      }
    }
  }

  private fun updateRightPanel(treeNode: CheckedTreeNode?) {
    rightPanel.removeAll()
    currentEditor = null
    when (val item = treeNode?.userObject) {
      is InlayGroup -> {
        addDescription(item.description)
      }
      is InlayGroupSettingProvider -> {
        addDescription(item.group.description)
        rightPanel.add(item.component)
      }
      is Language -> {
        configureLanguageNode(treeNode)?.let {
          configurePreview((treeNode.firstChild as CheckedTreeNode).userObject as InlayProviderSettingsModel, treeNode)
        }
      }
      is InlayProviderSettingsModel -> {
        if (item.isMergedNode && item.description == null) {
          configureLanguageNode(treeNode)
        }
        if (item.description != null) {
          addDescription(item.description)
        }
        if (item.component !is JPanel || item.component.componentCount > 0) {
          item.component.border = JBUI.Borders.empty()
          rightPanel.add(item.component)
        }
        if (treeNode.isLeaf) {
          addPreview(item.getCasePreview(null) ?: item.previewText, item, null, treeNode)
        }
        else if (item.isMergedNode) {
          configurePreview(item, treeNode)
        }
      }
      is ImmediateConfigurable.Case -> {
        val parent = treeNode.parent as CheckedTreeNode
        val model = parent.userObject as InlayProviderSettingsModel
        addDescription(model.getCaseDescription(item))
        val preview = model.getCasePreview(item)
        addPreview(preview, model, item, treeNode)
      }
    }
    if (treeNode != null) {
      InlayHintsSettings.instance().saveLastViewedProviderId(getProviderId(treeNode))
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun configurePreview(item: InlayProviderSettingsModel, treeNode: CheckedTreeNode) {
    val previewText = item.getCasePreview(null) ?: item.previewText
    if (previewText != null) {
      addPreview(previewText, item, null, treeNode)
    }
    else {
      for (case in item.cases) {
        val preview = item.getCasePreview(case)
        if (preview != null) {
          addPreview(preview, item, case, treeNode)
          break
        }
      }
    }
  }

  private fun configureLanguageNode(treeNode: CheckedTreeNode): String? {
    val description = ((treeNode.parent as CheckedTreeNode).userObject as InlayGroup).description
    addDescription(description)
    return description
  }

  private fun addPreview(previewText: String?,
                         model: InlayProviderSettingsModel,
                         case: ImmediateConfigurable.Case?,
                         treeNode: CheckedTreeNode) {
    if (previewText != null) {
      val editorTextField = createEditor(model.getCasePreviewLanguage(null) ?: model.language, project) { editor ->
        currentEditor = editor
        PREVIEW_KEY.set(editor, treeNode)
        CASE_KEY.set(editor, case)
        updateHints(editor, model, case)
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

  private fun updateHints(editor: Editor, model: InlayProviderSettingsModel, case: ImmediateConfigurable.Case?) {
    val fileType = getFileTypeForPreview(model)
    ReadAction.nonBlocking(Callable {
      val file = model.createFile(project, fileType, editor.document, case?.id)
      val continuation = model.collectData(editor, file)
      continuation
    })
      .finishOnUiThread(ModalityState.stateForComponent(this)) { continuation ->
        ApplicationManager.getApplication().runWriteAction {
          continuation.run()
        }
      }
      .expireWhen { editor.isDisposed }
      .inSmartMode(project)
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun addDescription(@Nls s: String?) {
    val htmlLabel = SwingHelper.createHtmlLabel((s ?: ""), null, null)
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
      is InlayGroupSettingProvider -> {
        item.reset()
        node.isChecked = item.isEnabled
      }
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

  private fun isCaseEnabled(item: ImmediateConfigurable.Case, parent: TreeNode, settings: InlayHintsSettings): Boolean {
    return item.value &&
           ((parent as CheckedTreeNode).userObject as InlayProviderSettingsModel).isEnabled &&
           settings.hintsEnabledGlobally()
  }

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
    DeclarativeInlayHintsPassFactory.resetModificationStamp()
    InlayHintsPassFactoryInternal.restartDaemonUpdatingHints(project, "InlaySettingsPanel.apply()")
  }

  private fun apply(node: CheckedTreeNode, settings: InlayHintsSettings) {
    if (!hintsEnabledGlobally(node, settings)) {
      // skip settings applying if hints are disabled globally, and there is no enabled checkbox
      // it is needed to avoid overriding the settings by global inlay toggle
      return
    }
    node.children().toList().forEach { apply(it as CheckedTreeNode, settings) }
    when (val item = node.userObject) {
      is InlayGroupSettingProvider -> {
        item.isEnabled = node.isChecked
        item.apply()
      }
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

  private fun hintsEnabledGlobally(root: CheckedTreeNode, settings: InlayHintsSettings): Boolean {
    return settings.hintsEnabledGlobally() || isAnyCheckboxEnabled(root)
  }

  private fun isAnyCheckboxEnabled(node: CheckedTreeNode): Boolean {
    val children = node.children().toList()
    if (children.isEmpty()) {
      return when (node.userObject) {
        is InlayGroupSettingProvider,
        is InlayProviderSettingsModel,
        is ImmediateConfigurable.Case -> {
          node.isChecked
        } else -> {
          false
        }
      }
    } else {
      return children.any { isAnyCheckboxEnabled(it as CheckedTreeNode) }
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
      is InlayGroupSettingProvider -> {
        if (item.isModified())
          return true
      }
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

  private inner class InlaySettingsTreeRenderer : CheckboxTree.CheckboxTreeCellRenderer(true, true),
                                                  UiInspectorTreeRendererContextProvider {
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

    override fun getUiInspectorContext(tree: JTree, value: Any?, row: Int): List<PropertyBean> {
      if (value !is DefaultMutableTreeNode) return emptyList()
      val result = mutableListOf<PropertyBean>()

      when (val item = value.userObject) {
        is InlayGroupSettingProvider -> {
          result.add(PropertyBean("Inlay Group Key", item.group.key, true))
        }
        is InlayGroup -> {
          result.add(PropertyBean("Inlay Group Key", item.key, true))
        }
        is InlayProviderSettingsModel -> {
          result.add(PropertyBean("Inlay Provider Model ID", item.id, true))
        }
        is ImmediateConfigurable.Case -> {
          result.add(PropertyBean("Inlay ImmediateConfigurable ID", item.id, true))
        }
      }
      return result
    }
  }
}