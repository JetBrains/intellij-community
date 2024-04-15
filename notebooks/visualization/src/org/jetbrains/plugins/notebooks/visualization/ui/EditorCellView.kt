package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Disposer
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.ui.visualization.*
import org.jetbrains.plugins.notebooks.visualization.*
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayController
import java.awt.*
import javax.swing.JComponent

class EditorCellView(
  private val editor: EditorEx,
  private val intervals: NotebookCellLines,
  internal var intervalPointer: NotebookIntervalPointer
) {

  private var _controllers: List<NotebookCellInlayController> = emptyList()
  val controllers: List<NotebookCellInlayController>
    get() = _controllers + ((input.component as? ControllerEditorCellViewComponent)?.controller?.let { listOf(it) } ?: emptyList())

  private val interval get() = intervalPointer.get() ?: error("Invalid interval")

  val input: EditorCellInput = EditorCellInput(
    editor,
    { currentComponent: EditorCellViewComponent? ->
      val currentController = (currentComponent as? ControllerEditorCellViewComponent)?.controller
      val controller = getInputFactories().firstNotNullOfOrNull {
        failSafeCompute(it, editor, currentController?.let { listOf(it) }
                                    ?: emptyList(), intervals.intervals.listIterator(interval.ordinal))
      }
      if (controller != null) {
        if (controller == currentController) {
          currentComponent
        }
        else {
          ControllerEditorCellViewComponent(controller)
        }
      }
      else {
        TextEditorCellViewComponent(editor, intervalPointer)
      }
    }, intervalPointer).also {
    it.addViewComponentListener(object : EditorCellViewComponentListener {
      override fun componentBoundaryChanged(location: Point, size: Dimension) {
        updateBoundaries()
      }
    })
  }

  private var _location: Point = Point(0, 0)

  val location: Point get() = _location

  private var _size: Dimension = Dimension(0, 0)

  val size: Dimension get() = _size

  private var output: EditorCellOutput? = null

  private var selected = false

  private var mouseOver = false

  init {
    update()
    updateSelection(false)
  }

  private fun updateBoundaries() {
    val y = input.location.y
    _location = Point(0, y)
    val currentOutput = output
    _size = Dimension(
      editor.contentSize.width,
      if (currentOutput == null)
        input.size.height
      else
        currentOutput.size.height + currentOutput.location.y - y
    )
  }

  fun dispose() {
    _controllers.forEach { controller ->
      disposeController(controller)
    }
    input.dispose()
    output?.dispose()
    removeCellHighlight()
  }

  private fun disposeController(controller: NotebookCellInlayController) {
    val inlay = controller.inlay
    inlay.renderer.asSafely<JComponent>()?.let { DataManager.removeDataProvider(it) }
    Disposer.dispose(inlay)
  }

  fun update() {
    val otherFactories = NotebookCellInlayController.Factory.EP_NAME.extensionList
      .filter { it !is NotebookCellInlayController.InputFactory }

    val controllersToDispose = _controllers.toMutableSet()
    _controllers = if (!editor.isDisposed) {
      otherFactories.mapNotNull { factory -> failSafeCompute(factory, editor, _controllers, intervals.intervals.listIterator(interval.ordinal)) }
    }
    else {
      emptyList()
    }
    controllersToDispose.removeAll(_controllers.toSet())
    controllersToDispose.forEach { disposeController(it) }
    for (controller in controllers) {
      val inlay = controller.inlay
      inlay.renderer.asSafely<JComponent>()?.let { component ->
        val oldProvider = DataManager.getDataProvider(component)
        if (oldProvider != null && oldProvider !is NotebookCellDataProvider) {
          LOG.error("Overwriting an existing CLIENT_PROPERTY_DATA_PROVIDER. Old provider: $oldProvider")
        }
        DataManager.removeDataProvider(component)
        DataManager.registerDataProvider(component, NotebookCellDataProvider(editor, component) { interval })
      }
    }
    input.update()
    output?.dispose()
    val outputController = controllers.filterIsInstance<NotebookOutputInlayController>().firstOrNull()
    if (outputController != null) {
      output = EditorCellOutput(editor, outputController)
      updateCellHighlight()
      updateFolding()
    }
    updateBoundaries()
    updateCellHighlight()
  }

  private fun getInputFactories(): Sequence<NotebookCellInlayController.Factory> {
    return NotebookCellInlayController.Factory.EP_NAME.extensionList.asSequence()
      .filter { it is NotebookCellInlayController.InputFactory }
  }

  private fun failSafeCompute(factory: NotebookCellInlayController.Factory,
                              editor: Editor,
                              controllers: Collection<NotebookCellInlayController>,
                              intervalIterator: ListIterator<NotebookCellLines.Interval>): NotebookCellInlayController? {
    try {
      return factory.compute(editor as EditorImpl, controllers, intervalIterator)
    }
    catch (t: Throwable) {
      thisLogger().error("${factory.javaClass.name} shouldn't throw exceptions at NotebookCellInlayController.Factory.compute(...)", t)
      return null
    }
  }

  fun updatePositions() {
    input.updatePositions()
    output?.updatePositions()
  }

  fun onViewportChanges() {
    input.onViewportChange()
    output?.onViewportChange()
  }

  fun setGutterAction(action: AnAction) {
    input.setGutterAction(action)
  }

  fun mouseExited() {
    mouseOver = false
    updateFolding()
  }

  fun mouseEntered() {
    mouseOver = true
    updateFolding()
  }

  private fun removeCellHighlight() {
    val interval = intervalPointer.get() ?: return
    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val overlappingIterator = editor.markupModel.overlappingIterator(startOffset, endOffset)
    val toRemove = try {
      overlappingIterator
        .asSequence()
        .filter { it.lineMarkerRenderer is NotebookLineMarkerRenderer }
        .toList()
    }
    finally {
      overlappingIterator.dispose()
    }
    toRemove.forEach { editor.markupModel.removeHighlighter(it) }
  }

  private fun updateCellHighlight() {
    removeCellHighlight()
    val interval = intervalPointer.get() ?: error("Invalid interval")
    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    editor.markupModel.addRangeHighlighter(
      null,
      startOffset,
      endOffset,
      HighlighterLayer.FIRST - 100,  // Border should be seen behind any syntax highlighting, selection or any other effect.
      HighlighterTargetArea.LINES_IN_RANGE
    ).also {
      it.lineMarkerRenderer = NotebookGutterLineMarkerRenderer(interval)
    }

    if (interval.type == NotebookCellLines.CellType.CODE && editor.notebookAppearance.shouldShowCellLineNumbers() && editor.editorKind != EditorKind.DIFF) {
      editor.markupModel.addRangeHighlighter(
        null,
        startOffset,
        endOffset,
        HighlighterLayer.FIRST - 99,  // Border should be seen behind any syntax highlighting, selection or any other effect.
        HighlighterTargetArea.LINES_IN_RANGE
      ).also {
        it.lineMarkerRenderer = NotebookCellLineNumbersLineMarkerRenderer(it)
      }
    }

    if (interval.type == NotebookCellLines.CellType.CODE) {
      editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
        o.lineMarkerRenderer = NotebookCodeCellBackgroundLineMarkerRenderer(o)
      }
    }
    else if (editor.editorKind != EditorKind.DIFF) {
      editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
        o.lineMarkerRenderer = NotebookTextCellBackgroundLineMarkerRenderer(o)
      }
    }

    for (controller: NotebookCellInlayController in controllers) {
      controller.createGutterRendererLineMarker(editor, interval)
    }
  }

  fun updateSelection(value: Boolean) {
    selected = value
    updateFolding()
    updateCellHighlight()
  }

  private fun updateFolding() {
    input.updateSelection(selected)
    output?.updateSelection(selected)
    if (mouseOver || selected) {
      input.showFolding()
      output?.showFolding()
    }
    else {
      input.hideFolding()
      output?.hideFolding()
    }
  }

  inner class NotebookGutterLineMarkerRenderer(private val interval: NotebookCellLines.Interval) : NotebookLineMarkerRenderer() {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      editor as EditorImpl

      @Suppress("NAME_SHADOWING")
      g.create().use { g ->
        g as Graphics2D

        val visualLineStart = editor.xyToVisualPosition(Point(0, g.clip.bounds.y)).line
        val visualLineEnd = editor.xyToVisualPosition(Point(0, g.clip.bounds.run { y + height })).line
        val logicalLineStart = editor.visualToLogicalPosition(VisualPosition(visualLineStart, 0)).line
        val logicalLineEnd = editor.visualToLogicalPosition(VisualPosition(visualLineEnd, 0)).line

        if (interval.lines.first > logicalLineEnd || interval.lines.last < logicalLineStart) return

        paintBackground(editor, g, r, interval)
      }
    }

    fun paintBackground(editor: EditorImpl,
                        g: Graphics,
                        r: Rectangle,
                        interval: NotebookCellLines.Interval) {
      val notebookCellInlayManager = NotebookCellInlayManager.get(editor) ?: throw AssertionError("Register inlay manager first")

      for (controller: NotebookCellInlayController in notebookCellInlayManager.inlaysForInterval(interval)) {
        controller.paintGutter(editor, g, r, interval)
      }
    }
  }

  private data class NotebookCellDataProvider(
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

  companion object {
    private val LOG = logger<EditorCell>()
  }
}