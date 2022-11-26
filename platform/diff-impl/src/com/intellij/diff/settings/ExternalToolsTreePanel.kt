// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalTool
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup
import com.intellij.diff.tools.external.ExternalDiffToolUtil
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SmartExpander
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PathUtilRt
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
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

internal class ExternalToolsTreePanel(private val models: ExternalToolsModels) {
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

    private val toolOutputEditor = ConsoleViewUtil.setupConsoleEditor(null, false, false).also {
      it.settings.additionalLinesCount = 3
    }
    private var toolOutputConsole: MyTestOutputConsole? = null

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

      Disposer.register(disposable) { toolOutputConsole?.let { Disposer.dispose(it) } }

      init()
    }

    override fun createCenterPanel(): JComponent = panel {
      lateinit var argumentPatternDescription: JEditorPane

      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.group")) {
        cell(groupField).align(AlignX.FILL)
      }.visible(!isEditMode)
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.program.path")) {
        cell(programPathField).align(AlignX.FILL)
      }
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.tool.name")) {
        cell(toolNameField).align(AlignX.FILL).validationOnApply { toolFieldValidation(groupField.item, it.text) }
      }
      row(DiffBundle.message("settings.external.tool.tree.add.dialog.field.argument.pattern")) {
        cell(argumentPatternField).align(AlignX.FILL)
      }
      row {
        cell(isMergeTrustExitCode).align(AlignX.FILL)
          .visibleIf(object : ComponentPredicate() {
            override fun addListener(listener: (Boolean) -> Unit) {
              groupField.addItemListener {
                val isMergeGroup = invoke()
                testDiffButton.isVisible = !isMergeGroup
                testThreeSideDiffButton.isVisible = !isMergeGroup
                testMergeButton.isVisible = isMergeGroup

                if (!isEditMode) {
                  argumentPatternField.text =
                    if (isMergeGroup) MERGE_TOOL_DEFAULT_ARGUMENT_PATTERN
                    else DIFF_TOOL_DEFAULT_ARGUMENT_PATTERN
                }

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
      row {
        argumentPatternDescription = comment(createDescription(ExternalToolGroup.DIFF_TOOL), DEFAULT_COMMENT_WIDTH).component
      }
      row {
        val isMergeGroup = isMergeTrustExitCode.isVisible
        cell(testDiffButton).visible(!isMergeGroup)
        cell(testThreeSideDiffButton).visible(!isMergeGroup)
        cell(testMergeButton).visible(isMergeGroup)
      }.topGap(TopGap.MEDIUM)
      row {
        cell(toolOutputEditor.component)
          .align(Align.FILL)
          .applyToComponent { preferredSize = JBUI.size(400, 150) }
      }.resizableRow()
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

    @NlsContexts.DetailedDescription
    private fun createDescription(toolGroup: ExternalToolGroup): String {
      val title = DiffBundle.message("settings.external.tools.parameters.description")
      val argumentPattern = when (toolGroup) {
        ExternalToolGroup.DIFF_TOOL -> DiffBundle.message("settings.external.tools.parameters.diff")
        ExternalToolGroup.MERGE_TOOL -> DiffBundle.message("settings.external.tools.parameters.merge")
      }

      return "$title<br>$argumentPattern"
    }

    private fun showTestDiff() {
      ExternalDiffToolUtil.testDiffTool2(null, createExternalTool(), resetToolOutputConsole())
    }

    private fun showTestThreeDiff() {
      ExternalDiffToolUtil.testDiffTool3(null, createExternalTool(), resetToolOutputConsole())
    }

    private fun showTestMerge() {
      ExternalDiffToolUtil.testMergeTool(null, createExternalTool(), resetToolOutputConsole())
    }

    @RequiresEdt
    private fun resetToolOutputConsole(): ExternalDiffToolUtil.TestOutputConsole {
      toolOutputConsole?.let { Disposer.dispose(it) }
      toolOutputConsole = MyTestOutputConsole(toolOutputEditor)
      return toolOutputConsole!!
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

  private class MyTestOutputConsole(private val editor: Editor) : ExternalDiffToolUtil.TestOutputConsole, Disposable {
    private val document = editor.document
    private val modalityState = ModalityState.stateForComponent(editor.component)

    private var isDisposed: Boolean = false
    private var wasTerminated: Boolean = false // better handling for out-of-order events

    init {
      document.setText("")
    }

    override fun getComponent(): JComponent = editor.component

    override fun appendOutput(outputType: Key<*>, line: String) {
      appendText("$outputType: $line", false)
    }

    override fun processTerminated(exitCode: Int) {
      appendText(DiffBundle.message("settings.external.tools.test.process.exit.text", exitCode), true)
    }

    private fun appendText(text: String, isTermination: Boolean) {
      runInEdt(modalityState) {
        if (isDisposed) return@runInEdt // the next test session has started
        if (isTermination) wasTerminated = true

        val offset = if (!isTermination && wasTerminated && document.lineCount > 1) {
          // the last line is termination line, insert output next-to-last-line
          document.getLineStartOffset(document.lineCount - 2) // -2, as process termination line also ends with \n
        }
        else {
          // insert into the end
          document.textLength
        }

        val line = if (text.endsWith('\n')) text else text + '\n'
        document.insertString(offset, line)
      }
    }

    override fun dispose() {
      isDisposed = true
    }
  }
}