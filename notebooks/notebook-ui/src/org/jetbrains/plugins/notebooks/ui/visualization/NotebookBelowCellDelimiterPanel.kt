package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import java.time.ZonedDateTime

class NotebookBelowCellDelimiterPanel(
  val editor: EditorImpl,
  private val isExecutable: Boolean,
  private val cellTags: List<String>,
  val cellNum: Int,
  isRenderedMarkdown: Boolean,
  executionCount: Int?,
  initStatusIcon: Icon?,
  initTooltipText: String?,
  initExecutionDurationText: String?,
  private val scope: CoroutineScope,
) : JPanel(BorderLayout()) {
  private val notebookAppearance = editor.notebookAppearance
  private val plusTagButtonSize = JBUI.scale(18)
  private val tagsSpacing = JBUI.scale(6)
  private val delimiterHeight = when (editor.editorKind.isDiff()) {
    true -> getJupyterCellSpacing(editor) / 2
    false -> editor.notebookAppearance.cellBorderHeight / 4
  }
  private var executionLabel: JLabel? = null

  private var elapsedStartTime: ZonedDateTime? = null
  private val updateElapsedTimeDelay = 100L
  private var elapsedTimeJob: Job? = null

  init {
    updateBackgroundColor()
    border = BorderFactory.createEmptyBorder(delimiterHeight, 0, delimiterHeight, 0)
    cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

    val addingTagsRow = (cellTags.isNotEmpty() && !isRenderedMarkdown && Registry.`is`("jupyter.cell.metadata.tags", false))
    if (addingTagsRow) add(createTagsRow(), BorderLayout.EAST)
    updateExecutionStatus(initTooltipText, executionCount, initStatusIcon, initExecutionDurationText)
  }

  private fun createExecutionLabel(): JLabel {
    return JLabel().apply {
      font = EditorUtil.getEditorFont()
      foreground = UIUtil.getLabelInfoForeground()
    }
  }

  @NlsSafe
  private fun getExecutionLabelText(executionCount: Int?, durationText: String?): String {
    val executionCountText = getExecutionCountLabelText(executionCount)
    val durationLabelText = durationText ?: ""
    val labelText = "$executionCountText $durationLabelText"
    return labelText
  }

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
    // however, the toolbar max height must be not greater than 18, which seemed to be non-trivial
    val action = ActionManager.getInstance().getAction("JupyterCellAddTagInlayAction") ?: return null
    val originalIcon = AllIcons.Expui.General.Add
    val transparentIcon = IconLoader.getTransparentIcon(originalIcon)

    return JButton().apply {
      icon = transparentIcon
      preferredSize = Dimension(plusTagButtonSize, plusTagButtonSize)
      isContentAreaFilled = false
      isFocusPainted = false
      isBorderPainted = false
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

      addMouseListener(createAddTagButtonHoverListener(originalIcon, transparentIcon))
      addActionListener(createAddTagButtonActionListener(action))
    }
  }

  private fun getExecutionCountLabelText(executionCount: Int?) = when {
      editor.editorKind != EditorKind.MAIN_EDITOR -> ""
      executionCount == null -> ""
      else -> "[$executionCount]"
    }

  private fun createAddTagButtonHoverListener(originalIcon: Icon, transparentIcon: Icon) = object : MouseAdapter() {
    override fun mouseEntered(e: MouseEvent) { (e.source as JButton).icon = originalIcon }
    override fun mouseExited(e: MouseEvent) { (e.source as JButton).icon = transparentIcon }
  }

  private fun createAddTagButtonActionListener(action: AnAction): ActionListener {
    return ActionListener {
      val dataContext = DataContext { dataId ->
        when (dataId) {
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

  private fun updateBackgroundColor() {
    background = when (isExecutable) {
      true -> notebookAppearance.getCodeCellBackground(editor.colorsScheme) ?: editor.colorsScheme.defaultBackground
      false -> editor.colorsScheme.defaultBackground
    }
  }

  private fun isExecutionCountDefined(executionCount: Int?): Boolean = executionCount?.let { it > 0 } ?: false

  @Suppress("USELESS_ELVIS")
  override fun updateUI() {
    // This method is called within constructor of JPanel, at this time state is not yet initialized, reference is null.
    editor ?: return
    updateBackgroundColor()
    super.updateUI()
  }

  fun updateExecutionStatus(
    @NlsSafe tooltipText: String?, executionCount: Int?, statusIcon: Icon?, @NlsSafe executionDurationText: String?
  ) {
    val showStatus = isExecutionCountDefined(executionCount) || (tooltipText != null && statusIcon != AllIcons.Expui.General.GreenCheckmark)
    if (showStatus) {
      getOrCreateExecutionLabel().apply {
        text = getExecutionLabelText(executionCount, executionDurationText)
        icon = statusIcon
        toolTipText = tooltipText
      }
    } else {
      executionLabel?.let { remove(it) }
      executionLabel = null
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