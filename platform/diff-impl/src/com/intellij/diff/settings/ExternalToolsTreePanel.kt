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
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ColoredListCellRenderer
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
import com.intellij.util.PathUtilRt
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

internal class ExternalToolsTreePanel(private val models: ExternalToolsModels) : BorderLayoutPanel() {
  private val treeModel = models.treeModel
  private val root = treeModel.root as CheckedTreeNode
  private val tree = CheckboxTree(ExternalToolsTreeCellRenderer(), root).apply {
    visibleRowCount = 8
    model = treeModel
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(mouseEvent: MouseEvent) {
        val treePath = selectionPath ?: return
        if (mouseEvent.clickCount == 2 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
          mouseEvent.consume()
          when ((treePath.lastPathComponent as DefaultMutableTreeNode).userObject) {
            is ExternalDiffSettings.ExternalToolGroup -> {
              if (isExpanded(treePath)) collapsePath(treePath)
              else expandPath(treePath)
            }
            is ExternalDiffSettings.ExternalTool -> editData()
            else -> {}
          }
        }
      }
    })
  }

  val component: JComponent

  init {
    val decoratedTree = ToolbarDecorator.createDecorator(tree)
      .setAddAction { addTool() }
      .setRemoveAction { removeData() }
      .setEditAction { editData() }
      .disableUpDownActions()
      .createPanel()

    component = decoratedTree
  }

  fun getData(): MutableMap<ExternalDiffSettings.ExternalToolGroup, List<ExternalDiffSettings.ExternalTool>> {
    val data = mutableMapOf<ExternalDiffSettings.ExternalToolGroup, List<ExternalDiffSettings.ExternalTool>>()

    for (group in root.children()) {
      val groupNode = group as DefaultMutableTreeNode

      val tools = mutableListOf<ExternalDiffSettings.ExternalTool>()
      for (child in group.children()) {
        val childNode = child as DefaultMutableTreeNode
        val tool = childNode.userObject as ExternalDiffSettings.ExternalTool
        tools.add(tool)
      }

      data[groupNode.userObject as ExternalDiffSettings.ExternalToolGroup] = tools
    }

    return data
  }

  fun updateData(value: Map<ExternalDiffSettings.ExternalToolGroup, List<ExternalDiffSettings.ExternalTool>>) {
    root.removeAllChildren()

    value.toSortedMap().forEach { (group, tools) ->
      val groupNode = DefaultMutableTreeNode(group)
      tools.forEach { groupNode.add(DefaultMutableTreeNode(it)) }
      treeModel.insertNodeInto(groupNode, root, root.childCount)
    }

    treeModel.nodeStructureChanged(root)
    TreeUtil.expandAll(tree)
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

  private fun findGroupNode(externalToolGroup: ExternalDiffSettings.ExternalToolGroup): DefaultMutableTreeNode {
    for (child in root.children()) {
      val treeNode = child as DefaultMutableTreeNode
      val valueNode = treeNode.userObject as ExternalDiffSettings.ExternalToolGroup
      if (valueNode == externalToolGroup) return treeNode
    }

    val groupNode = DefaultMutableTreeNode(externalToolGroup)
    treeModel.insertNodeInto(groupNode, root, root.childCount)

    return groupNode
  }

  private fun removeData() {
    val treePath = tree.selectionPath ?: return
    val node = treePath.lastPathComponent as DefaultMutableTreeNode
    if (node.userObject !is ExternalDiffSettings.ExternalTool) {
      Messages.showWarningDialog(DiffBundle.message("settings.external.tool.tree.remove.group.warning.message"),
                                 DiffBundle.message("settings.external.tool.tree.remove.group.warning.title"))
      return
    }

    val parentNode = treePath.parentPath.lastPathComponent as DefaultMutableTreeNode
    val toolGroup = parentNode.userObject as ExternalDiffSettings.ExternalToolGroup

    val externalTool = node.userObject as ExternalDiffSettings.ExternalTool
    if (isConfigurationRegistered(externalTool, toolGroup)) {
      Messages.showWarningDialog(DiffBundle.message("settings.external.tool.tree.remove.warning.message"),
                                 DiffBundle.message("settings.external.tool.tree.remove.warning.title"))
      return
    }

    val dialog = MessageDialogBuilder.okCancel(DiffBundle.message("settings.external.diff.table.remove.dialog.title"),
                                               DiffBundle.message("settings.external.diff.table.remove.dialog.message"))
    if (dialog.guessWindowAndAsk()) {
      treeModel.removeNodeFromParent(node)
    }
  }

  private fun editData() {
    val treePath = tree.selectionPath ?: return
    val node = treePath.lastPathComponent as DefaultMutableTreeNode
    if (node.userObject !is ExternalDiffSettings.ExternalTool) {
      return
    }

    val currentTool = node.userObject as ExternalDiffSettings.ExternalTool
    val dialog = AddToolDialog(currentTool)
    if (dialog.showAndGet()) {
      val editedTool = dialog.createExternalTool()
      val groupNode = findGroupNode(dialog.getToolGroup())
      val toolGroup = groupNode.userObject as ExternalDiffSettings.ExternalToolGroup

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

  private class ExternalToolsTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
    override fun customizeRenderer(tree: JTree?,
                                   value: Any?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean) {
      val renderer = textRenderer
      val text = when (val item = (value as DefaultMutableTreeNode).userObject) {
        null -> return
        is ExternalDiffSettings.ExternalToolGroup -> item.groupName // NON-NLS
        is ExternalDiffSettings.ExternalTool -> item.name // NON-NLS
        else -> item.toString() // NON-NLS
      }

      renderer.append(text)
    }
  }

  private inner class AddToolDialog(
    private val oldToolName: String? = null,
    private val isEditMode: Boolean = false,
  ) : DialogWrapper(null) {
    private val groupField = ComboBox(
      arrayOf(ExternalDiffSettings.ExternalToolGroup.DIFF_TOOL, ExternalDiffSettings.ExternalToolGroup.MERGE_TOOL)
    ).apply {
      renderer = object : ColoredListCellRenderer<ExternalDiffSettings.ExternalToolGroup>() {
        override fun customizeCellRenderer(list: JList<out ExternalDiffSettings.ExternalToolGroup>,
                                           value: ExternalDiffSettings.ExternalToolGroup,
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
      addBrowseFolderListener(DiffBundle.message("select.external.diff.program.dialog.title"),
                              null,
                              null,
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
      text = createDescription(ExternalDiffSettings.ExternalToolGroup.DIFF_TOOL)
    }

    constructor(externalTool: ExternalDiffSettings.ExternalTool) : this(externalTool.name, true) {
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
                val isMergeEnabled = invoke()
                testDiffButton.isVisible = !isMergeEnabled
                testThreeSideDiffButton.isVisible = !isMergeEnabled
                testMergeButton.isVisible = isMergeEnabled

                argumentPatternField.text =
                  if (isMergeEnabled) MERGE_TOOL_DEFAULT_ARGUMENT_PATTERN
                  else DIFF_TOOL_DEFAULT_ARGUMENT_PATTERN

                argumentPatternDescription.text =
                  if (isMergeEnabled) createDescription(ExternalDiffSettings.ExternalToolGroup.MERGE_TOOL)
                  else createDescription(ExternalDiffSettings.ExternalToolGroup.DIFF_TOOL)

                listener(isMergeEnabled)
              }
            }

            override fun invoke(): Boolean {
              val item = groupField.selectedItem as ExternalDiffSettings.ExternalToolGroup
              return item == ExternalDiffSettings.ExternalToolGroup.MERGE_TOOL
            }
          })
      }
      row { cell(argumentPatternDescription) }
      row {
        val isMergeEnabled = isMergeTrustExitCode.isEnabled
        cell(testDiffButton).visible(!isMergeEnabled)
        cell(testThreeSideDiffButton).visible(!isMergeEnabled)
        cell(testMergeButton).visible(isMergeEnabled)
      }.topGap(TopGap.MEDIUM)
    }

    fun createExternalTool(): ExternalDiffSettings.ExternalTool = ExternalDiffSettings.ExternalTool(
      toolNameField.text,
      programPathField.text,
      argumentPatternField.text,
      isMergeTrustExitCode.isEnabled && isMergeTrustExitCode.isSelected,
      groupField.item
    )

    fun getToolGroup(): ExternalDiffSettings.ExternalToolGroup = groupField.item

    private fun toolFieldValidation(toolGroup: ExternalDiffSettings.ExternalToolGroup, toolName: String): ValidationInfo? {
      if (toolName.isEmpty()) {
        return ValidationInfo(DiffBundle.message("settings.external.tool.tree.validation.empty"))
      }

      if (isToolAlreadyExist(toolGroup, toolName) && toolName != oldToolName) {
        return ValidationInfo(DiffBundle.message("settings.external.tool.tree.validation.already.exist", toolGroup, toolName))
      }

      return null
    }

    private fun isToolAlreadyExist(toolGroup: ExternalDiffSettings.ExternalToolGroup, toolName: String): Boolean {
      val isNodeExist = TreeUtil.findNode(findGroupNode(toolGroup)) { node ->
        when (val externalTool = node.userObject) {
          is ExternalDiffSettings.ExternalTool -> externalTool.name == toolName
          else -> false // skip root
        }
      }

      return isNodeExist != null
    }

    private fun createDescription(toolGroup: ExternalDiffSettings.ExternalToolGroup): String {
      val title = DiffBundle.message("settings.external.tools.parameters.description")
      val argumentPattern = when (toolGroup) {
        ExternalDiffSettings.ExternalToolGroup.DIFF_TOOL -> DiffBundle.message("settings.external.tools.parameters.diff")
        ExternalDiffSettings.ExternalToolGroup.MERGE_TOOL -> DiffBundle.message("settings.external.tools.parameters.merge")
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

  private fun ListTableModel<ExternalDiffSettings.ExternalToolConfiguration>.updateEntities(
    toolGroup: ExternalDiffSettings.ExternalToolGroup,
    oldTool: ExternalDiffSettings.ExternalTool,
    newTool: ExternalDiffSettings.ExternalTool
  ) {
    items.forEach { configuration ->
      if (toolGroup == ExternalDiffSettings.ExternalToolGroup.DIFF_TOOL) {
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