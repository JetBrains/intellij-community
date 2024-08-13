package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookCodeCellBackgroundLineMarkerRenderer
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookLineMarkerRenderer
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookTextCellBackgroundLineMarkerRenderer
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.*
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController.InputFactory
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.ProgressStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
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

  init {
    recreateControllers()
    updateSelection(false)
  }

  private fun createEditorCellInput() =
    EditorCellInput(editor, getInputFactories().firstOrNull { it.supports(editor, cell) } ?: fallbackInputFactory, cell).also {
      add(it)
    }


  fun postInitInlays() {
    updateControllers()
  }

  override fun doDispose() {
    _controllers.forEach { controller ->
      disposeController(controller)
    }
    input.dispose()
    outputs?.let { Disposer.dispose(it) }
    removeCellHighlight()
  }

  private fun disposeController(controller: NotebookCellInlayController) {
    val inlay = controller.inlay
    inlay.renderer.asSafely<JComponent>()?.let { DataManager.removeDataProvider(it) }
    Disposer.dispose(inlay)
  }

  fun update(updateContext: UpdateContext) {
    input.update()
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
    updateOutputs()
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

  fun updateInput() {
    updateCellHighlight()
    input.updateInput()
  }

  internal fun updateOutputs() {
    if (hasOutputs()) {
      if (outputs == null) {
        outputs = EditorCellOutputs(editor, { interval })
          .also {
            Disposer.register(this, it)
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
        Disposer.dispose(it)
        remove(it)
      }
      outputs = null
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

  fun setGutterAction(action: AnAction?) {
    input.setGutterAction(action)
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

    addCellHighlighter {
      editor.markupModel.addRangeHighlighter(
        null,
        startOffset,
        endOffset,
        HighlighterLayer.FIRST - 100,  // Border should be seen behind any syntax highlighting, selection or any other effect.
        HighlighterTargetArea.LINES_IN_RANGE
      ).apply {
        lineMarkerRenderer = NotebookGutterLineMarkerRenderer(interval)
      }
    }

    if (interval.type == NotebookCellLines.CellType.CODE) {
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

    if (interval.type == NotebookCellLines.CellType.CODE && editor.notebookAppearance.shouldShowCellLineNumbers() && editor.editorKind != EditorKind.DIFF) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighter(
          null,
          startOffset,
          endOffset,
          HighlighterLayer.FIRST - 99,  // Border should be seen behind any syntax highlighting, selection or any other effect.
          HighlighterTargetArea.LINES_IN_RANGE
        )
      }
    }

    if (interval.type == NotebookCellLines.CellType.CODE) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookCodeCellBackgroundLineMarkerRenderer(o)
        }
      }
    }
    else if (editor.editorKind != EditorKind.DIFF) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookTextCellBackgroundLineMarkerRenderer(o)
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
    input.runCellButton?.visible = mouseOver || selected
  }


  override fun calculateBounds(): Rectangle {
    val inputBounds = input.calculateBounds()
    val currentOutputs = outputs

    val outputRectangle = currentOutputs?.calculateBounds()?.takeIf { !it.isEmpty }
    val height = outputRectangle?.let { it.height + it.y - inputBounds.y } ?: inputBounds.height

    return Rectangle(0, inputBounds.y, editor.contentSize.width, height)
  }

  fun updateExecutionStatus(executionCount: Int?, progressStatus: ProgressStatus?, startTime: ZonedDateTime?, endTime: ZonedDateTime?) {
    _controllers.filterIsInstance<CellExecutionStatusView>().firstOrNull()
      ?.updateExecutionStatus(executionCount, progressStatus, startTime, endTime)
    input.runCellButton?.updateGutterAction(progressStatus)
  }

  inner class NotebookGutterLineMarkerRenderer(private val interval: NotebookCellLines.Interval) : NotebookLineMarkerRenderer() {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      editor as EditorImpl

      g.create().use { g2 ->
        g2 as Graphics2D

        val visualLineStart = editor.xyToVisualPosition(Point(0, g2.clip.bounds.y)).line
        val visualLineEnd = editor.xyToVisualPosition(Point(0, g2.clip.bounds.run { y + height })).line
        val logicalLineStart = editor.visualToLogicalPosition(VisualPosition(visualLineStart, 0)).line
        val logicalLineEnd = editor.visualToLogicalPosition(VisualPosition(visualLineEnd, 0)).line

        if (interval.lines.first > logicalLineEnd || interval.lines.last < logicalLineStart) return

        paintBackground(editor, g2, r, interval)
      }
    }

    private fun paintBackground(
      editor: EditorImpl,
      g: Graphics,
      r: Rectangle,
      interval: NotebookCellLines.Interval,
    ) {
      for (controller: NotebookCellInlayController in controllers) {
        controller.paintGutter(editor, g, r, interval)
      }
      outputs?.paintGutter(editor, g, r)
    }
  }

  internal data class NotebookCellDataProvider(
    val editor: Editor,
    val component: JComponent,
    val intervalProvider: () -> NotebookCellLines.Interval,
  ) : DataProvider {
    override fun getData(key: String): Any? =
      when (key) {
        NOTEBOOK_CELL_LINES_INTERVAL_DATA_KEY.name -> intervalProvider()
        PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> component
        PlatformDataKeys.EDITOR.name -> editor
        else -> null
      }
  }

  override fun doGetInlays(): Sequence<Inlay<*>> {
    return controllers.map { it.inlay }.asSequence()
  }

  fun switchToEditMode(ctx: UpdateContext) {
    input.switchToEditMode(ctx)
  }

  fun switchToCommandMode(ctx: UpdateContext) {
    input.switchToCommandMode(ctx)
  }

  fun requestCaret() {
    input.requestCaret()
  }
}

/**
 * Renders rectangle in the right part of editor to make filled code cells look like rectangles with margins.
 * But mostly it's used as a token to filter notebook cell highlighters.
 */
private object NotebookCellHighlighterRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    editor as EditorImpl
    @Suppress("NAME_SHADOWING") g.create().use { g ->
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