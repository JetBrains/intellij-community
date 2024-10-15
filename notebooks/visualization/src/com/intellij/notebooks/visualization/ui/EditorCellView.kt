package com.intellij.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.notebooks.ui.visualization.*
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isDiffKind
import com.intellij.notebooks.visualization.*
import com.intellij.notebooks.visualization.NotebookCellInlayController.InputFactory
import com.intellij.notebooks.visualization.context.NotebookDataContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.asSafely
import java.awt.Graphics
import java.awt.Rectangle
import java.time.ZonedDateTime
import javax.swing.JComponent
import kotlin.reflect.KClass

private val fallbackInputFactory = object : InputFactory {
  override fun createComponent(editor: EditorImpl, cell: EditorCell): EditorCellViewComponent {
    return TextEditorCellViewComponent(editor, cell)
  }

  override fun supports(editor: EditorImpl, cell: EditorCell): Boolean {
    return true
  }
}

class EditorCellView(
  private val editor: EditorImpl,
  private val intervals: NotebookCellLines,
  var cell: EditorCell,
  private val cellInlayManager: NotebookCellInlayManager,
) : EditorCellViewComponent(), Disposable {

  private val uiSettings = UISettings.getInstance()

  private var _controllers: List<NotebookCellInlayController> = emptyList()

  private val controllers: List<NotebookCellInlayController>
    get() = _controllers + ((input.component as? ControllerEditorCellViewComponent)?.controller?.let { listOf(it) } ?: emptyList())

  private val intervalPointer: NotebookIntervalPointer
    get() = cell.intervalPointer

  private val interval: NotebookCellLines.Interval
    get() = intervalPointer.get() ?: error("Invalid interval")

  private val cellHighlighters = mutableListOf<RangeHighlighter>()

  val input: EditorCellInput = createEditorCellInput()

  var outputs: EditorCellOutputs? = null
    private set

  var selected = false
    set(value) {
      field = value
      updateFolding()
      updateRunButtonVisibility()
      updateCellHighlight()
    }

  private var mouseOver = false

  // We are storing last offsets for highlighters to prevent highlighters unnecessary recreation on same values.
  private var lastHighLightersOffsets: IntRange? = null

  var disableActions: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      updateRunButtonVisibility()
    }

  init {
    cell.source.afterChange(this) {
      updateInput()
    }
    cell.selected.afterChange(this) { selected ->
      this.selected = selected
    }
    this.selected = cell.selected.get()
    cell.executionStatus.afterChange(this) { execution ->
      updateExecutionStatus(execution.count, execution.status, execution.startTime, execution.endTime)
    }
    val executionStatus = cell.executionStatus.get()
    updateExecutionStatus(executionStatus.count, executionStatus.status, executionStatus.startTime, executionStatus.endTime)
    recreateControllers()
    updateSelection(false)
    updateOutputs()
    updateControllers()
  }

  private fun createEditorCellInput() =
    EditorCellInput(editor, getInputFactories().firstOrNull { it.supports(editor, cell) } ?: fallbackInputFactory, cell).also {
      add(it)
    }

  override fun dispose() {
    super.dispose()
    _controllers.forEach { controller ->
      disposeController(controller)
    }
    removeCellHighlight()
  }

  private fun disposeController(controller: NotebookCellInlayController) {
    val inlay = controller.inlay
    inlay.renderer.asSafely<JComponent>()?.let { DataManager.removeDataProvider(it) }
    Disposer.dispose(inlay)
  }

  fun update(updateContext: UpdateContext) {
    input.update()
    updateOutputs()
    recreateControllers()
    updateControllers()
    updateCellFolding(updateContext)
  }

  private fun updateControllers() {
    for (controller in controllers) {
      val inlay = controller.inlay
      inlay.renderer.asSafely<JComponent>()?.let { component ->
        val oldProvider = DataManager.getDataProvider(component)
        if (oldProvider != null && oldProvider !is NotebookCellDataProvider) {
          thisLogger().error("Overwriting an existing CLIENT_PROPERTY_DATA_PROVIDER. Old provider: $oldProvider")
        }
        DataManager.removeDataProvider(component)
        DataManager.registerDataProvider(component, NotebookCellDataProvider(editor, component) { interval })
      }
    }
    updateCellHighlight()
  }

  private fun recreateControllers() {
    val otherFactories = NotebookCellInlayController.Factory.EP_NAME.extensionList
      .filter { it !is InputFactory }
    val controllersToDispose = _controllers.toMutableSet()
    _controllers = if (!editor.isDisposed) {
      otherFactories.mapNotNull { factory -> failSafeCompute(factory, editor, _controllers, intervals.intervals.listIterator(interval.ordinal)) }
    }
    else {
      emptyList()
    }
    controllersToDispose.removeAll(_controllers.toSet())
    controllersToDispose.forEach { disposeController(it) }
  }

  private fun updateInput() = runInEdt {
    updateCellHighlight()
    input.updateInput()
  }

  private fun updateOutputs() = runInEdt {
    if (hasOutputs()) {
      if (outputs == null) {
        outputs = EditorCellOutputs(editor, cell).also {
          add(it)
        }
        updateCellHighlight()
        updateFolding()
      }
      else {
        outputs?.update()
      }
    }
    else {
      outputs?.let {
        remove(it)
        outputs = null
      }
    }
  }

  private fun hasOutputs() = interval.type == NotebookCellLines.CellType.CODE
                             && (editor.editorKind != EditorKind.DIFF || Registry.`is`("jupyter.diff.viewer.output"))

  private fun getInputFactories(): Sequence<InputFactory> {
    return cellInlayManager.getInputFactories()
  }

  private fun failSafeCompute(
    factory: NotebookCellInlayController.Factory,
    editor: Editor,
    controllers: Collection<NotebookCellInlayController>,
    intervalIterator: ListIterator<NotebookCellLines.Interval>,
  ): NotebookCellInlayController? {
    return failSafeCompute { factory.compute(editor as EditorImpl, controllers, intervalIterator) }
  }

  private fun <T> failSafeCompute(
    factory: () -> T,
  ): T? {
    try {
      return factory()
    }
    catch (t: Throwable) {
      thisLogger().error("${factory.javaClass.name} shouldn't throw exceptions at NotebookCellInlayController.Factory.compute(...)", t)
      return null
    }
  }

  fun onViewportChanges() {
    input.onViewportChange()
    outputs?.onViewportChange()
  }

  fun mouseExited() {
    mouseOver = false
    updateFolding()
    updateRunButtonVisibility()
  }

  fun mouseEntered() {
    mouseOver = true
    updateFolding()
    updateRunButtonVisibility()
  }

  inline fun <reified T : Any> getExtension(): T? {
    return getExtension(T::class)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : Any> getExtension(type: KClass<T>): T? {
    return controllers.firstOrNull { type.isInstance(it) } as? T
  }

  fun addCellHighlighter(provider: () -> RangeHighlighter) {
    val highlighter = provider()
    cellHighlighters.add(highlighter)
  }

  private fun removeCellHighlight() {
    cellHighlighters.forEach {
      it.dispose()
    }
    cellHighlighters.clear()
  }

  private fun updateCellHighlight() {
    val interval = intervalPointer.get() ?: error("Invalid interval")

    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)

    val range = IntRange(startOffset, endOffset)
    if (interval.lines == lastHighLightersOffsets) {
      return
    }
    lastHighLightersOffsets = range

    removeCellHighlight()

    // manages the cell background + clips background on the right below the scroll bar
    if (interval.type == NotebookCellLines.CellType.CODE && !editor.isDiffKind()) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighter(
          startOffset,
          endOffset,
          // Code cell background should be seen behind any syntax highlighting, selection or any other effect.
          HighlighterLayer.FIRST - 100,
          TextAttributes().apply {
            backgroundColor = editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme)
          },
          HighlighterTargetArea.LINES_IN_RANGE
        ).apply {
          customRenderer = NotebookCellHighlighterRenderer
        }
      }
    }

    // draws gray vertical rectangles between line numbers and the leftmost border of the text
    if (interval.type == NotebookCellLines.CellType.CODE) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookCodeCellBackgroundLineMarkerRenderer(o)
        }
      }
    } else if (editor.editorKind != EditorKind.DIFF) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookTextCellBackgroundLineMarkerRenderer(o)
        }
      }
    }

    if (uiSettings.presentationMode || DistractionFreeModeController.isDistractionFreeModeEnabled()) {  // See PY-74597
      addCellHighlighter {
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookCodeCellBackgroundLineMarkerRenderer(o, presentationModeMasking = true)
        }
      }
    }

    for (controller: NotebookCellInlayController in controllers) {
      controller.createGutterRendererLineMarker(editor, interval, this)
    }
  }

  fun updateSelection(value: Boolean) {
    selected = value
    updateFolding()
    updateRunButtonVisibility()
    updateCellHighlight()
  }

  private fun updateFolding() {
    input.folding.visible = mouseOver || selected
    input.folding.selected = selected
    outputs?.foldingsVisible = mouseOver || selected
    outputs?.foldingsSelected = selected
  }

  private fun updateRunButtonVisibility() {
    input.runCellButton?.visible = !disableActions && (mouseOver || selected)
  }

  override fun calculateBounds(): Rectangle {
    val inputBounds = input.calculateBounds()
    val currentOutputs = outputs

    val outputRectangle = currentOutputs?.calculateBounds()?.takeIf { !it.isEmpty }
    val height = outputRectangle?.let { it.height + it.y - inputBounds.y } ?: inputBounds.height

    return Rectangle(0, inputBounds.y, editor.contentSize.width, height)
  }

  private fun updateExecutionStatus(executionCount: Int?, progressStatus: ProgressStatus?, startTime: ZonedDateTime?, endTime: ZonedDateTime?) {
    _controllers.filterIsInstance<CellExecutionStatusView>().firstOrNull()
      ?.updateExecutionStatus(executionCount, progressStatus, startTime, endTime)
    input.runCellButton?.updateGutterAction(progressStatus)
  }

  internal data class NotebookCellDataProvider(
    val editor: Editor,
    val component: JComponent,
    val intervalProvider: () -> NotebookCellLines.Interval,
  ) : DataProvider {
    override fun getData(key: String): Any? =
      when (key) {
        NotebookDataContext.NOTEBOOK_CELL_LINES_INTERVAL.name -> intervalProvider()
        PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> component
        PlatformDataKeys.EDITOR.name -> editor
        else -> null
      }
  }

  override fun doGetInlays(): Sequence<Inlay<*>> {
    return controllers.map { it.inlay }.asSequence()
  }

  fun requestCaret() {
    input.requestCaret()
  }
}

/**
 * Renders rectangle in the right part of the editor to make filled code cells look like rectangles with margins.
 * But mostly it's used as a token to filter notebook cell highlighters.
 */
@Suppress("DuplicatedCode")
private object NotebookCellHighlighterRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    editor as EditorImpl
    graphics.create().use { g ->
      val scrollbarWidth = editor.scrollPane.verticalScrollBar.width
      val oldBounds = g.clipBounds
      val visibleArea = editor.scrollingModel.visibleArea
      g.setClip(
        visibleArea.x + visibleArea.width - scrollbarWidth,
        oldBounds.y,
        scrollbarWidth,
        oldBounds.height
      )

      g.color = editor.colorsScheme.defaultBackground
      g.clipBounds.run {
        val fillX = if (editor.editorKind == EditorKind.DIFF && editor.isMirrored) x + 20 else x
        g.fillRect(fillX, y, width, height)
      }
    }
  }
}