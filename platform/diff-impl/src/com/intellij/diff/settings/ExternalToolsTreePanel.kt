// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.ThreesideMergeRequest
import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalTool
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup
import com.intellij.diff.tools.external.ExternalDiffToolUtil
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SmartExpander
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PathUtilRt
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

internal class ExternalToolsTreePanel(private val models: ExternalToolsModels) : BorderLayoutPanel() {
  private var treeState: TreeState
  private val treeModel = models.treeModel
  private val root = treeModel.root as DefaultMutableTreeNode
  private val tree = Tree(treeModel).apply {
    visibleRowCount = 8
    isRootVisible = false
    cellRenderer = ExternalToolsTreeCellRenderer()
    treeState = TreeState.createOn(this)

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(mouseEvent: MouseEvent) {
        val treePath = selectionPath ?: return
        if (mouseEvent.clickCount == 2 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
          mouseEvent.consume()
          val node = treePath.lastPathComponent as DefaultMutableTreeNode
          when (node.userObject) {
            is ExternalTool -> editData()
            else -> {}
          }
        }
      }
    })
    addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        treeState = TreeState.createOn(this@apply)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) {
        treeState = TreeState.createOn(this@apply)
      }
    })

    SmartExpander.installOn(this)
  }

  val component: JComponent

  init {
    val decoratedTree = ToolbarDecorator.createDecorator(tree)
      .setAddAction { addTool() }
      .setRemoveActionUpdater { isExternalToolSelected(tree.selectionPath) }
      .setRemoveAction { removeData() }
      .setEditActionUpdater { isExternalToolSelected(tree.selectionPath) }
      .setEditAction { editData() }
      .disableUpDownActions()
      .createPanel()

    component = decoratedTree
  }

  fun onModified(settings: ExternalDiffSettings): Boolean = treeModel.toMap() != settings.externalTools

  fun onApply(settings: ExternalDiffSettings) {
    settings.externalTools = treeModel.toMap()
    treeState = TreeState.createOn(tree)
  }

  fun onReset(settings: ExternalDiffSettings) {
    treeModel.update(settings.externalTools)
    treeState.applyTo(tree)
  }

  private fun addTool() {
    val dialog = AddToolDialog()
    if (dialog.showAndGet()) {
      val tool = dialog.createExternalTool()
      val node = DefaultMutableTreeNode(tool)
      val groupNode = findGroupNode(dialog.getToolGroup())

      treeModel.insertNodeInto(node, groupNode, groupNode.childCount)
      treeModel.nodeChanged(root)
      tree.expandPath(TreePath(groupNode.path))
    }
  }

  private fun findGroupNode(externalToolGroup: ExternalToolGroup): DefaultMutableTreeNode {
    for (child in root.children()) {
      val treeNode = child as DefaultMutableTreeNode
      val valueNode = treeNode.userObject as ExternalToolGroup
      if (valueNode == externalToolGroup) return treeNode
    }

    val groupNode = DefaultMutableTreeNode(externalToolGroup)
    treeModel.insertNodeInto(groupNode, root, root.childCount)

    return groupNode
  }

  private fun removeData() {
    val treePath = tree.selectionPath ?: return
    val node = treePath.lastPathComponent as DefaultMutableTreeNode
    val parentNode = treePath.parentPath.lastPathComponent as DefaultMutableTreeNode
    val toolGroup = parentNode.userObject as ExternalToolGroup

    val externalTool = node.userObject as ExternalTool
    if (isConfigurationRegistered(externalTool, toolGroup)) {
      Messages.showWarningDialog(DiffBundle.message("settings.external.tool.tree.remove.warning.message"),
                                 DiffBundle.message("settings.external.tool.tree.remove.warning.title"))
      return
    }

    val dialog = MessageDialogBuilder.okCancel(DiffBundle.message("settings.external.diff.table.remove.dialog.title"),
                                               DiffBundle.message("settings.external.diff.table.remove.dialog.message"))
    if (dialog.guessWindowAndAsk()) {
      treeModel.removeNodeFromParent(node)
      if (parentNode.childCount == 0) {
        treeModel.removeNodeFromParent(parentNode)
      }
    }
  }

  private fun editData() {
    val treePath = tree.selectionPath ?: return
    val node = treePath.lastPathComponent as DefaultMutableTreeNode
    if (node.userObject !is ExternalTool) {
      return
    }

    val currentTool = node.userObject as ExternalTool
    val dialog = AddToolDialog(currentTool)
    if (dialog.showAndGet()) {
      val editedTool = dialog.createExternalTool()
      val groupNode = findGroupNode(dialog.getToolGroup())
      val toolGroup = groupNode.userObject as ExternalToolGroup

      node.userObject = editedTool
      treeModel.nodeChanged(node)

      models.tableModel.updateEntities(toolGroup, currentTool, editedTool)
    }
  }

  private fun isConfigurationRegistered(externalTool: ExternalTool, externalToolGroup: ExternalToolGroup): Boolean {
    val configurations = models.tableModel.items

    return when (externalToolGroup) {
      ExternalToolGroup.DIFF_TOOL -> configurations.any { it.diffToolName == externalTool.name }
      ExternalToolGroup.MERGE_TOOL -> configurations.any { it.mergeToolName == externalTool.name }
    }
  }

  private fun isExternalToolSelected(selectionPath: TreePath?): Boolean {
    if (selectionPath == null) return false

    val node = selectionPath.lastPathComponent as DefaultMutableTreeNode
    return when (node.userObject) {
      is ExternalTool -> true
      else -> false
    }
  }

  private class ExternalToolsTreeCellRenderer : TreeCellRenderer {
    private val renderer = SimpleColoredComponent()

    override fun getTreeCellRendererComponent(tree: JTree, value: Any,
                                              selected: Boolean, expanded: Boolean,
                                              leaf: Boolean, row: Int, hasFocus: Boolean): Component {
      return renderer.apply {
        val node = value as DefaultMutableTreeNode
        val text = when (val userObject = node.userObject) {
          null -> "" // Special for root (not visible in tree)
          is ExternalToolGroup -> userObject.groupName // NON-NLS
          is ExternalTool -> userObject.name // NON-NLS
          else -> userObject.toString() // NON-NLS
        }

        clear()
        append(text)
      }
    }
  }

  private inner class AddToolDialog(
    private val oldToolName: String? = null,
    private val isEditMode: Boolean = false,
  ) : DialogWrapper(null) {
    private val groupField = ComboBox(
      arrayOf(ExternalToolGroup.DIFF_TOOL, ExternalToolGroup.MERGE_TOOL)
    ).apply {
      renderer = object : ColoredListCellRenderer<ExternalToolGroup>() {
        override fun customizeCellRenderer(list: JList<out ExternalToolGroup>,
                                           value: ExternalToolGroup,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean) {
          append(value.groupName) // NON-NLS
        }
      }
    }

    private var isAutocompleteToolName = true
    private val toolNameField = JBTextField().apply {
      document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(event: DocumentEvent) {
          if (isFocusOwner) isAutocompleteToolName = false
        }

        override fun removeUpdate(event: DocumentEvent) {}
        override fun changedUpdate(event: DocumentEvent) {}
      })
    }
    private val programPathField = TextFieldWithBrowseButton().apply {
      addBrowseFolderListener(DiffBundle.message("select.external.program.dialog.title"), null, null,
                              FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
      textField.document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(event: DocumentEvent) {
          if (isAutocompleteToolName) {
            val guessToolName = StringUtil.capitalize(PathUtilRt.getFileName(text))
            toolNameField.text = guessToolName
          }
        }

        override fun removeUpdate(event: DocumentEvent) {}
        override fun changedUpdate(event: DocumentEvent) {}
      })
    }
    private val argumentPatternField = JBTextField(DIFF_TOOL_DEFAULT_ARGUMENT_PATTERN)
    private val isMergeTrustExitCode = JBCheckBox(DiffBundle.message("settings.external.diff.trust.process.exit.code"))

    private val testDiffButton = JButton(DiffBundle.message("settings.external.diff.test.diff")).apply {
      addActionListener { showTestDiff() }
    }
    private val testThreeSideDiffButton = JButton(DiffBundle.message("settings.external.diff.test.three.side.diff")).apply {
      addActionListener { showTestThreeDiff() }
    }
    private val testMergeButton = JButton(DiffBundle.message("settings.external.diff.test.merge")).apply {
      addActionListener { showTestMerge() }
    }
    private val argumentPatternDescription = DslLabel(DslLabelType.COMMENT).apply {
      maxLineLength = DEFAULT_COMMENT_WIDTH
      text = createDescription(ExternalToolGroup.DIFF_TOOL)
    }

    constructor(externalTool: ExternalTool) : this(externalTool.name, true) {
      isAutocompleteToolName = false

      toolNameField.text = externalTool.name
      programPathField.text = externalTool.programPath
      argumentPatternField.text = externalTool.argumentPattern
      isMergeTrustExitCode.isSelected = externalTool.isMergeTrustExitCode
      groupField.selectedItem = externalTool.groupName

      title = DiffBundle.message("settings.external.tool.tree.edit.dialog.title")
    }

    init {
      title = DiffBundle.message("settings.external.tool.tree.add.dialog.title")

      init()
    }

    override fun createCenterPanel(): JComponent = panel {
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.group")) {
        cell(groupField).horizontalAlign(HorizontalAlign.FILL)
      }.visible(!isEditMode)
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.program.path")) {
        cell(programPathField).horizontalAlign(HorizontalAlign.FILL)
      }
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.tool.name")) {
        cell(toolNameField).horizontalAlign(HorizontalAlign.FILL).validationOnApply { toolFieldValidation(groupField.item, it.text) }
      }
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.argument.pattern")) {
        cell(argumentPatternField).horizontalAlign(HorizontalAlign.FILL)
      }
      row {
        cell(isMergeTrustExitCode).horizontalAlign(HorizontalAlign.FILL)
          .visibleIf(object : ComponentPredicate() {
            override fun addListener(listener: (Boolean) -> Unit) {
              groupField.addItemListener {
                val isMergeGroup = invoke()
                testDiffButton.isVisible = !isMergeGroup
                testThreeSideDiffButton.isVisible = !isMergeGroup
                testMergeButton.isVisible = isMergeGroup

                argumentPatternField.text =
                  if (isMergeGroup) MERGE_TOOL_DEFAULT_ARGUMENT_PATTERN
                  else DIFF_TOOL_DEFAULT_ARGUMENT_PATTERN

                argumentPatternDescription.text =
                  if (isMergeGroup) createDescription(ExternalToolGroup.MERGE_TOOL)
                  else createDescription(ExternalToolGroup.DIFF_TOOL)

                listener(isMergeGroup)
              }
            }

            override fun invoke(): Boolean {
              val item = groupField.selectedItem as ExternalToolGroup
              return item == ExternalToolGroup.MERGE_TOOL
            }
          })
      }
      row { cell(argumentPatternDescription) }
      row {
        val isMergeGroup = isMergeTrustExitCode.isVisible
        cell(testDiffButton).visible(!isMergeGroup)
        cell(testThreeSideDiffButton).visible(!isMergeGroup)
        cell(testMergeButton).visible(isMergeGroup)
      }.topGap(TopGap.MEDIUM)
    }

    fun createExternalTool(): ExternalTool = ExternalTool(toolNameField.text,
                                                          programPathField.text,
                                                          argumentPatternField.text,
                                                          isMergeTrustExitCode.isVisible && isMergeTrustExitCode.isSelected,
                                                          groupField.item)

    fun getToolGroup(): ExternalToolGroup = groupField.item

    private fun toolFieldValidation(toolGroup: ExternalToolGroup, toolName: String): ValidationInfo? {
      if (toolName.isEmpty()) {
        return ValidationInfo(DiffBundle.message("settings.external.tool.tree.validation.empty"))
      }

      if (isToolAlreadyExist(toolGroup, toolName) && toolName != oldToolName) {
        return ValidationInfo(DiffBundle.message("settings.external.tool.tree.validation.already.exist", toolGroup, toolName))
      }

      return null
    }

    private fun isToolAlreadyExist(toolGroup: ExternalToolGroup, toolName: String): Boolean {
      val isNodeExist = TreeUtil.findNode(findGroupNode(toolGroup)) { node ->
        when (val externalTool = node.userObject) {
          is ExternalTool -> externalTool.name == toolName
          else -> false // skip root
        }
      }

      return isNodeExist != null
    }

    private fun createDescription(toolGroup: ExternalToolGroup): String {
      val title = DiffBundle.message("settings.external.tools.parameters.description")
      val argumentPattern = when (toolGroup) {
        ExternalToolGroup.DIFF_TOOL -> DiffBundle.message("settings.external.tools.parameters.diff")
        ExternalToolGroup.MERGE_TOOL -> DiffBundle.message("settings.external.tools.parameters.merge")
      }

      return "$title<br>$argumentPattern"
    }

    private fun showTestDiff() {
      try {
        val factory = DiffContentFactory.getInstance()
        val contents = listOf(factory.create(DiffBundle.message("settings.external.diff.left.file.content"), FileTypes.PLAIN_TEXT),
                              factory.create(DiffBundle.message("settings.external.diff.right.file.content"), FileTypes.PLAIN_TEXT))
        val titles = listOf("Left", "Right")
        ExternalDiffToolUtil.execute(null, createExternalTool(), contents, titles, null)
      }
      catch (e: Exception) {
        Messages.showErrorDialog(e.message, DiffBundle.message("error.cannot.show.diff"))
      }
    }

    private fun showTestThreeDiff() {
      try {
        val factory = DiffContentFactory.getInstance()
        val contents = listOf(factory.create(DiffBundle.message("settings.external.diff.left.file.content"), FileTypes.PLAIN_TEXT),
                              factory.create(DiffBundle.message("settings.external.diff.base.file.content"), FileTypes.PLAIN_TEXT),
                              factory.create(DiffBundle.message("settings.external.diff.right.file.content"), FileTypes.PLAIN_TEXT))
        val titles = listOf("Left", "Base", "Right")
        ExternalDiffToolUtil.execute(null, createExternalTool(), contents, titles, null)
      }
      catch (e: Exception) {
        Messages.showErrorDialog(e.message, DiffBundle.message("error.cannot.show.diff"))
      }
    }

    private fun showTestMerge() {
      try {
        val factory = DiffRequestFactory.getInstance()
        val document = DocumentImpl(DiffBundle.message("settings.external.diff.original.output.file.content"))

        val callback = { result: MergeResult ->
          val message = when (result) {
            MergeResult.CANCEL -> DiffBundle.message("settings.external.diff.merge.conflict.resolve.was.canceled")
            else -> DiffBundle.message("settings.external.diff.merge.conflict.resolve.successful",
                                       StringUtil.shortenPathWithEllipsis(document.text, 60))

          }
          Messages.showInfoMessage(message, DiffBundle.message("settings.external.diff.test.complete"))
        }
        val contents = listOf(DiffBundle.message("settings.external.diff.left.file.content"),
                              DiffBundle.message("settings.external.diff.base.file.content"),
                              DiffBundle.message("settings.external.diff.right.file.content"))
        val titles = listOf("Left", "Base", "Right")
        val request = factory.createMergeRequest(null, PlainTextFileType.INSTANCE, document, contents, null, titles, callback)
        ExternalDiffToolUtil.executeMerge(null, createExternalTool(), request as ThreesideMergeRequest, this.contentPanel)
      }
      catch (e: Exception) {
        Messages.showErrorDialog(e.message, DiffBundle.message("error.cannot.show.merge"))
      }
    }
  }

  private fun DefaultTreeModel.toMap(): MutableMap<ExternalToolGroup, List<ExternalTool>> {
    val root = this.root as DefaultMutableTreeNode
    val model = mutableMapOf<ExternalToolGroup, List<ExternalTool>>()

    for (group in root.children()) {
      if (group.childCount == 0) continue

      val groupNode = group as DefaultMutableTreeNode
      val tools = mutableListOf<ExternalTool>()
      for (child in group.children()) {
        val childNode = child as DefaultMutableTreeNode
        val tool = childNode.userObject as ExternalTool
        tools.add(tool)
      }

      model[groupNode.userObject as ExternalToolGroup] = tools
    }

    return model
  }

  private fun DefaultTreeModel.update(value: Map<ExternalToolGroup, List<ExternalTool>>) {
    val root = this.root as DefaultMutableTreeNode
    root.removeAllChildren()

    value.toSortedMap().forEach { (group, tools) ->
      if (tools.isEmpty()) return@forEach

      val groupNode = DefaultMutableTreeNode(group)
      tools.forEach { groupNode.add(DefaultMutableTreeNode(it)) }
      insertNodeInto(groupNode, root, root.childCount)
    }

    nodeStructureChanged(root)
  }

  private fun ListTableModel<ExternalDiffSettings.ExternalToolConfiguration>.updateEntities(
    toolGroup: ExternalToolGroup,
    oldTool: ExternalTool,
    newTool: ExternalTool
  ) {
    items.forEach { configuration ->
      if (toolGroup == ExternalToolGroup.DIFF_TOOL) {
        if (configuration.diffToolName == oldTool.name) {
          configuration.diffToolName = newTool.name
        }
      }
      else {
        if (configuration.mergeToolName == oldTool.name) {
          configuration.mergeToolName = newTool.name
        }
      }
    }
  }

  companion object {
    private const val DIFF_TOOL_DEFAULT_ARGUMENT_PATTERN = "%1 %2 %3"
    private const val MERGE_TOOL_DEFAULT_ARGUMENT_PATTERN = "%1 %2 %3 %4"
  }
}