// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.ThreesideMergeRequest
import com.intellij.diff.tools.external.ExternalDiffSettings
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
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

internal class ExternalToolsTreePanel(
  private val tableModel: ListTableModel<ExternalDiffSettings.ExternalToolConfiguration>
) : BorderLayoutPanel() {
  private val root = CheckedTreeNode()
  private val treeModel = DefaultTreeModel(root)
  private val tree = CheckboxTree(ExternalToolsTreeCellRenderer(), root).apply {
    model = treeModel
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(mouseEvent: MouseEvent) {
        if (mouseEvent.clickCount == 2 && selectionPath != null) {
          editData()
        }
      }
    })
  }

  init {
    val decoratedTree = ToolbarDecorator.createDecorator(tree)
      .setAddAction { addTool() }
      .setRemoveAction { removeData() }
      .setEditAction { editData() }
      .disableUpDownActions()
      .createPanel()

    JBUI.size(decoratedTree.preferredSize).withHeight(200).let {
      decoratedTree.minimumSize = it
      decoratedTree.preferredSize = it
    }
    add(decoratedTree, BorderLayout.CENTER)
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

    val externalTool = node.userObject as ExternalDiffSettings.ExternalTool
    if (ExternalDiffSettings.isConfigurationRegistered(externalTool)) {
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

      tableModel.updateEntities(toolGroup, currentTool, editedTool)
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

  private inner class AddToolDialog(private val oldToolName: String? = null) : DialogWrapper(null) {
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

    private val toolNameField = JBTextField()
    private val programPathField = TextFieldWithBrowseButton().apply {
      addBrowseFolderListener(DiffBundle.message("select.external.diff.program.dialog.title"),
                              null,
                              null,
                              FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
    }
    private val argumentPatternField = JBTextField()
    private val isMergeTrustExitCode = JBCheckBox(DiffBundle.message("settings.external.diff.trust.process.exit.code"))

    private lateinit var testDiffButton: JButton
    private lateinit var testThreeSideDiffButton: JButton
    private lateinit var testMergeButton: JButton

    constructor(externalTool: ExternalDiffSettings.ExternalTool) : this(externalTool.name) {
      toolNameField.text = externalTool.name
      programPathField.text = externalTool.programPath
      argumentPatternField.text = externalTool.argumentPattern
      
      groupField.isEnabled = false
    }

    init {
      JBUI.size(WINDOW_WIDTH, WINDOW_HEIGHT).let {
        rootPane.minimumSize = it
        rootPane.preferredSize = it
      }

      panel {
        row {
          testDiffButton = button(DiffBundle.message("settings.external.diff.test.diff")) {
            showTestDiff()
          }.component
          testThreeSideDiffButton = button(DiffBundle.message("settings.external.diff.test.three.side.diff")) {
            showTestThreeDiff()
          }.component
          testMergeButton = button(DiffBundle.message("settings.external.diff.test.merge")) {
            showTestMerge()
          }.component
        }
      }

      title = DiffBundle.message("settings.external.tool.tree.add.dialog.title")

      init()
    }

    override fun createCenterPanel(): JComponent = panel {
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.group")) {
        cell(groupField).horizontalAlign(HorizontalAlign.FILL)
      }
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.tool.name")) {
        cell(toolNameField).horizontalAlign(HorizontalAlign.FILL).validationOnApply { toolFieldValidation(groupField.item, it.text) }
      }
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.program.path")) {
        cell(programPathField).horizontalAlign(HorizontalAlign.FILL)
      }
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.argument.pattern")) {
        cell(argumentPatternField).horizontalAlign(HorizontalAlign.FILL)
      }
      row {
        cell(isMergeTrustExitCode).horizontalAlign(HorizontalAlign.FILL)
          .enabledIf(object : ComponentPredicate() {
            override fun addListener(listener: (Boolean) -> Unit) {
              groupField.addItemListener {
                val isMergeEnabled = invoke()
                testDiffButton.isVisible = !isMergeEnabled
                testThreeSideDiffButton.isVisible = !isMergeEnabled
                testMergeButton.isVisible = isMergeEnabled

                listener(isMergeEnabled)
              }
            }

            override fun invoke(): Boolean {
              val item = groupField.selectedItem as ExternalDiffSettings.ExternalToolGroup
              return item == ExternalDiffSettings.ExternalToolGroup.MERGE_TOOL
            }
          })
      }
      row {
        comment(DiffBundle.message("settings.diff.tools.parameters"))
      }
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
    private const val WINDOW_WIDTH = 400
    private const val WINDOW_HEIGHT = 400
  }
}