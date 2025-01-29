package com.intellij.notebooks.ui.visualization

import com.intellij.icons.AllIcons
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isDiffKind
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBBox
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.time.ZonedDateTime
import javax.swing.*

class NotebookBelowCellDelimiterPanel(
  val editor: EditorImpl,
  private val cellTags: List<String>,
  val cellIndex: Int,
  isRenderedMarkdown: Boolean,
  executionCount: Int?,
  initStatusIcon: Icon?,
  initTooltipText: String?,
  initExecutionDurationText: String?,
  private val scope: CoroutineScope,
) : JPanel(BorderLayout()) {
  private val tagsSpacing = JBUI.scale(6)
  private val delimiterHeight = when (editor.isOrdinaryNotebookEditor()) {
    true -> editor.notebookAppearance.cellBorderHeight / 4
    false -> NotebookEditorAppearanceUtils.getJupyterCellSpacing(editor) / 2
  }
  private var executionLabel: JLabel? = null

  private var elapsedStartTime: ZonedDateTime? = null
  private val updateElapsedTimeDelay = 100L
  private var elapsedTimeJob: Job? = null

  init {
    border = BorderFactory.createCompoundBorder(
      BorderFactory.createEmptyBorder(0, 0, 1, 1),
      BorderFactory.createEmptyBorder(delimiterHeight, 0, delimiterHeight, 0)
    )
    cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

    val addingTagsRow = (cellTags.isNotEmpty() && !isRenderedMarkdown && Registry.`is`("jupyter.cell.metadata.tags", false))
    if (addingTagsRow) add(createTagsRow(), BorderLayout.EAST)
    updateExecutionStatus(initTooltipText, executionCount, initStatusIcon, initExecutionDurationText)
  }

  fun setFrameVisible(isVisible: Boolean, frameColor: Color) {
    val frameBorder = when (isVisible) {
      true -> BorderFactory.createMatteBorder(0, 0, 1, 1, frameColor)
      else -> BorderFactory.createMatteBorder(0, 0, 1, 1, background)
    }

    border = BorderFactory.createCompoundBorder(
      frameBorder,
      BorderFactory.createEmptyBorder(delimiterHeight, 0, delimiterHeight, 0)
    )

    repaint()
  }

  private fun createExecutionLabel(): JLabel {
    return JLabel().apply {
      font = EditorUtil.getEditorFont()
      foreground = UIUtil.getLabelInfoForeground()
    }
  }

  private fun getExecutionLabelText(executionCount: Int?, durationText: String?): @NlsSafe String {
    val executionCountText = getExecutionCountLabelText(executionCount)
    val durationLabelText = durationText ?: ""
    val labelText = "$executionCountText $durationLabelText"
    return labelText
  }

  private fun createTagsRow(): Component {
    val tagsRow = JBBox.createHorizontalBox()
    tagsRow.add(createAddTagButton())

    cellTags.forEach { tag ->
      tagsRow.add(NotebookCellTagLabel(tag, cellIndex))
      tagsRow.add(Box.createHorizontalStrut(tagsSpacing))
    }
    return tagsRow
  }

  private fun createAddTagButton(): JComponent {
    val actionGroup = ActionManager.getInstance().getAction("JupyterCellAddTagInlayActionGroup") as ActionGroup
    val toolbar = ActionManager.getInstance().createActionToolbar("NotebookTagsPanel", actionGroup, /*horizontal =*/ true).apply {
      layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
      border = BorderFactory.createEmptyBorder()
      setMinimumButtonSize(JBUI.size(18, 18))
      targetComponent = this@NotebookBelowCellDelimiterPanel
    }
    return toolbar.component
  }

  private fun getExecutionCountLabelText(executionCount: Int?) = when {
    !editor.isOrdinaryNotebookEditor() -> ""
    executionCount == null -> ""
    executionCount == 0 -> ""
    else -> "[$executionCount]"
  }

  private fun isExecutionCountDefined(executionCount: Int?): Boolean = executionCount?.let { it > 0 } == true

  fun updateExecutionStatus(
    @NlsSafe tooltipText: String?,
    executionCount: Int?,
    statusIcon: Icon?,
    @NlsSafe executionDurationText: String?,
  ) {
    val showStatus = (isExecutionCountDefined(executionCount) || (tooltipText != null && statusIcon != AllIcons.General.GreenCheckmark))
                     && !editor.isDiffKind()

    if (showStatus) {
      getOrCreateExecutionLabel().apply {
        text = getExecutionLabelText(executionCount, executionDurationText)
        icon = statusIcon
        toolTipText = tooltipText
      }
    }
    else {  // temporary measure to fit the drag icon, see PY-65433
      getOrCreateExecutionLabel().apply {
        text = ""
        icon = AllIcons.Empty
        toolTipText = null
      }
    }
  }

  private fun updateElapsedTime(@NlsSafe elapsedText: String) = getOrCreateExecutionLabel().apply { text = elapsedText }

  fun startElapsedTimeUpdate(startTime: ZonedDateTime?, diffFormatter: (ZonedDateTime, ZonedDateTime) -> String) {
    startTime ?: return
    elapsedStartTime = startTime
    elapsedTimeJob?.cancel()

    elapsedTimeJob = scope.launch {
      val flow = flow {
        while (true) {
          elapsedStartTime?.let { startTime -> emit(diffFormatter(startTime, ZonedDateTime.now())) }
          delay(updateElapsedTimeDelay)
        }
      }

      flow.collect { elapsedText -> updateElapsedTime(elapsedText) }
    }
  }

  fun stopElapsedTimeUpdate() {
    elapsedTimeJob?.cancel()
    elapsedTimeJob = null
  }

  private fun getOrCreateExecutionLabel(): JLabel {
    return executionLabel ?: createExecutionLabel().also {
      add(it, BorderLayout.WEST)
      executionLabel = it
    }
  }
}