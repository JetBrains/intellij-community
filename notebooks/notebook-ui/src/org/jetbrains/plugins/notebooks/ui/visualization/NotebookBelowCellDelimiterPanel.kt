package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.*

class NotebookBelowCellDelimiterPanel(
  val editor: EditorImpl,
  @Nls private val tooltipText: String?,
  private val executionCount: Int?,
  private val statusIcon: Icon?,
  private val isExecutable: Boolean,
  private val cellTags: List<String>,
  val cellNum: Int
) : JPanel(BorderLayout()) {
  private val notebookAppearance = editor.notebookAppearance
  private var isCollapsed = getCollapsed()
  private val plusTagButtonSize = JBUI.scale(18)
  private val tagsSpacing = JBUI.scale(6)
  private val delimiterHeight =   when (editor.editorKind.isDiff()) {
    true -> getJupyterCellSpacing(editor) / 2
    false -> editor.notebookAppearance.cellBorderHeight / 4
  }

  init {
    updateBackgroundColor()
    border = BorderFactory.createEmptyBorder(delimiterHeight, 0, delimiterHeight, 0)

    val addingExecutionLabel = (!editor.editorKind.isDiff() && isExecutable && !isCollapsed)
    val addingTagsRow = (cellTags.isNotEmpty() && isExecutable && Registry.`is`("jupyter.cell.metadata.tags", false))

    if (addingExecutionLabel) add(createExecutionLabel(), BorderLayout.WEST)
    if (addingTagsRow) add(createTagsRow(), BorderLayout.EAST)  // // PY-72712
  }

  private fun createExecutionLabel(): JLabel {
    val executionCountText = executionCount?.let { if (it > 0) "[$it]" else "" } ?: ""
    return JLabel(executionCountText).apply {
      icon = statusIcon
      font = EditorUtil.getEditorFont()
      foreground = UIUtil.getLabelInfoForeground()
      toolTipText = tooltipText
    }
  }

  @Suppress("HardCodedStringLiteral")
  private fun createTagsRow(): Box {
    val tagsRow = Box.createHorizontalBox()
    val plusActionToolbar = createAddTagButton()
    tagsRow.add(plusActionToolbar)
    tagsRow.add(Box.createHorizontalStrut(tagsSpacing))

    cellTags.forEach { tag ->
      val tagLabel = NotebookCellTagLabel(tag, cellNum)
      tagsRow.add(tagLabel)
      tagsRow.add(Box.createHorizontalStrut(tagsSpacing))
    }
    return tagsRow
  }

  private fun createAddTagButton(): JButton? {
    // todo: refactor
    // ideally, a toolbar with a single action and targetComponent this should've done that
    // however, the toolbar max height must be not greater than 18, which seemed to be untrivial
    val action = ActionManager.getInstance().getAction("JupyterCellAddTagInlayAction") ?: return null

    return JButton().apply {
      icon = ExpUiIcons.General.Add
      preferredSize = Dimension(plusTagButtonSize, plusTagButtonSize)
      isContentAreaFilled = false
      isFocusPainted = false
      isBorderPainted = false
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

      addActionListener {
        val dataContext = DataContext { dataId ->
          when(dataId) {
            CommonDataKeys.EDITOR.name -> editor
            CommonDataKeys.PROJECT.name -> editor.project
            PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> this@NotebookBelowCellDelimiterPanel
            else -> null
          }
        }
        val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.EDITOR_INLAY, dataContext)
        action.actionPerformed(event)
      }
    }
  }

  private fun updateBackgroundColor() {
    background = when (isExecutable) {
      true -> notebookAppearance.getCodeCellBackground(editor.colorsScheme) ?: editor.colorsScheme.defaultBackground
      false -> editor.colorsScheme.defaultBackground
    }
  }

  private fun getCollapsed(): Boolean {
    if (cellTags.isNotEmpty()) return false
    return !isExecutionCountDefined() && (tooltipText == null || statusIcon == ExpUiIcons.General.GreenCheckmark)
  }

  private fun isExecutionCountDefined(): Boolean = executionCount?.let { it > 0 } ?: false

  @Suppress("USELESS_ELVIS")
  override fun updateUI() {
    // This method is called within constructor of JPanel, at this time state is not yet initialized, reference is null.
    editor ?: return
    updateBackgroundColor()
    super.updateUI()
  }

}
