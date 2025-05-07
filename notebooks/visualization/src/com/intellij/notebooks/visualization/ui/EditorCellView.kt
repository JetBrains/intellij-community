package com.intellij.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isDiffKind
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookCellHighlighterRenderer
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookCodeCellBackgroundLineMarkerRenderer
import com.intellij.notebooks.visualization.*
import com.intellij.notebooks.visualization.NotebookCellInlayController.InputFactory
import com.intellij.notebooks.visualization.context.NotebookDataContext
import com.intellij.notebooks.visualization.ui.EditorCell.ExecutionStatus
import com.intellij.notebooks.visualization.ui.cell.frame.EditorCellFrameManager
import com.intellij.notebooks.visualization.ui.cellsDnD.DropHighlightableCellPanel
import com.intellij.notebooks.visualization.ui.jupyterToolbars.NotebookCellActionsToolbarStateTracker
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
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.asSafely
import java.awt.Color
import java.awt.Rectangle
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
  val editor: EditorImpl,
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

  val interval: NotebookCellLines.Interval
    get() = intervalPointer.get() ?: error("Invalid interval")

  private val cellHighlighters = mutableListOf<RangeHighlighter>()

  val input: EditorCellInput = createEditorCellInput()

  var outputs: EditorCellOutputsView? = null
    private set

  val cellFrameManager: EditorCellFrameManager? =
    if (interval.type == NotebookCellLines.CellType.MARKDOWN && Registry.`is`("jupyter.markdown.cells.border")) {
      EditorCellFrameManager(editor, this, NotebookCellLines.CellType.MARKDOWN)
    }
    else if (Registry.`is`("jupyter.code.cells.border")) {
      EditorCellFrameManager(editor, this, NotebookCellLines.CellType.CODE)
    } else null

  var selected: Boolean = false
    set(value) {
      if (field == value) {
        return
      }

      field = value
      updateFolding()
      updateRunButtonVisibility()
      updateCellHighlight()
      updateCellActionsToolbarVisibility()
      cellFrameManager?.updateCellFrameShow(value, mouseOver)
    }

  private var mouseOver = false

  // We are storing last lines range for highlighters to prevent highlighters unnecessary recreation on the same lines.
  private var lastHighLightersLines: IntRange? = null

  var disableActions: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      updateRunButtonVisibility()
    }

  var isUnderDiff: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      updateCellActionsToolbarVisibility()
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
      updateExecutionStatus(execution)
    }
    updateExecutionStatus(cell.executionStatus.get())
    editor.notebookAppearance.codeCellBackgroundColor.afterChange(this) { backgroundColor ->
      updateCellHighlight(force = true)
    }
    cell.notebook.readOnly.afterChange(this) {
      updateRunButtonVisibility()
    }
    cell.notebook.showCellToolbar.afterChange(this) {
      updateCellActionsToolbarVisibility()
    }
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
    cellFrameManager?.let { Disposer.dispose(it) }
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
      controller.createGutterRendererLineMarker(editor, interval, cellView = this)
    }
  }

  private fun recreateControllers() = editor.updateManager.update { updateContext ->
    updateContext.addInlayOperation {
      val otherFactories = NotebookCellInlayController.Factory.EP_NAME.extensionList
        .filter { it !is InputFactory }
      val controllersToDispose = _controllers.toMutableSet()
      _controllers = if (!editor.isDisposed) {
        otherFactories.mapNotNull { factory ->
          val intervalIterator = intervals.intervals.listIterator(interval.ordinal)
          val cellInlayController = failSafeCompute(factory, editor, _controllers, intervalIterator)
          if (cellInlayController is Disposable) {
            Disposer.register(this, cellInlayController)
          }
          cellInlayController
        }
      }
      else {
        emptyList()
      }
      controllersToDispose.removeAll(_controllers.toSet())
      controllersToDispose.forEach { disposeController(it) }
      updateControllers()
    }
  }

  private fun updateInput() = runInEdt {
    updateCellHighlight()
    input.updateInput()
    checkAndRebuildInlays()
  }

  override fun doCheckAndRebuildInlays() {
    if (isInlaysBroken()) {
      recreateControllers()
    }
  }

  private fun isInlaysBroken(): Boolean {
    val inlaysOffsets = buildSet {
      add(editor.document.getLineStartOffset(interval.lines.first))
      add(editor.document.getLineEndOffset(interval.lines.last))
    }
    for (inlay in getInlays()) {
      if (!inlay.isValid || inlay.offset !in inlaysOffsets) {
        return true
      }
    }
    return false
  }

  private fun updateOutputs() = runInEdt {
    if (hasOutputs()) {
      if (outputs == null) {
        outputs = EditorCellOutputsView(editor, cell).also {
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
    cellFrameManager?.updateCellFrameShow(selected, mouseOver)
    updateCellActionsToolbarVisibility()
  }

  fun mouseEntered() {
    mouseOver = true
    updateFolding()
    updateRunButtonVisibility()
    cellFrameManager?.updateCellFrameShow(selected, mouseOver)
    updateCellActionsToolbarVisibility()
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

  private fun updateCellHighlight(force: Boolean = false) {
    val interval = intervalPointer.get() ?: error("Invalid interval")

    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)

    if (!force && interval.lines == lastHighLightersLines) {
      return
    }
    lastHighLightersLines = IntRange(interval.lines.first, interval.lines.last)

    removeCellHighlight()

    // manages the cell background and clips background on the right below the scroll bar
    if (interval.type == NotebookCellLines.CellType.CODE && !editor.isDiffKind()) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighter(
          startOffset,
          endOffset,
          // Code cell background should be seen behind any syntax highlighting, selection or any other effect.
          HighlighterLayer.FIRST - 100,
          TextAttributes().apply {
            backgroundColor = editor.notebookAppearance.codeCellBackgroundColor.get()
          },
          HighlighterTargetArea.LINES_IN_RANGE
        ).apply {
          setCustomRenderer(NotebookCellHighlighterRenderer)
        }
      }
    }

    // draws gray vertical rectangles between line numbers and the leftmost border of the text
    if (interval.type == NotebookCellLines.CellType.CODE) {
      addCellHighlighter {
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookCodeCellBackgroundLineMarkerRenderer(o, { input.component.calculateBounds().let { it.y to it.height } })
        }
      }
    }

    if (uiSettings.presentationMode || DistractionFreeModeController.isDistractionFreeModeEnabled()) {  // See PY-74597
      addCellHighlighter {
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false) { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookCodeCellBackgroundLineMarkerRenderer(o, { input.component.calculateBounds().let { it.y to it.height } }, presentationModeMasking = true)
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
    updateCellHighlight()
    cellFrameManager?.updateCellFrameShow(selected, mouseOver)
  }

  private fun updateFolding() {
    input.folding.visible = mouseOver || selected
    input.folding.selected = selected
    outputs?.foldingsVisible = mouseOver || selected
    outputs?.foldingsSelected = selected
  }

  private fun updateRunButtonVisibility() {
    input.runCellButton ?: return
    val isReadOnlyNotebook = editor.notebook?.readOnly?.get() == true
    val shouldBeVisible = !isReadOnlyNotebook && !disableActions && (mouseOver || selected)
    if (input.runCellButton.lastRunButtonVisibility == shouldBeVisible) return

    input.runCellButton.visible = shouldBeVisible
    input.runCellButton.lastRunButtonVisibility = shouldBeVisible
  }

  private fun updateCellActionsToolbarVisibility() {
    val toolbarManager = input.cellActionsToolbar ?: return
    if ((isUnderDiff == true)) return
    val targetComponent = _controllers.filterIsInstance<DataProviderComponent>().firstOrNull()?.retrieveDataProvider() ?: return
    val tracker = NotebookCellActionsToolbarStateTracker.get(editor) ?: return
    when {
      !cell.notebook.showCellToolbar.get() -> toolbarManager.hideToolbar()
      mouseOver -> toolbarManager.showToolbar(targetComponent)
      selected -> {
        // we show the toolbar only for the last selected cell
        if (tracker.lastSelectedCell == input) return
        tracker.updateLastSelectedCell(input)
        toolbarManager.showToolbar(targetComponent)
      }
      else -> toolbarManager.hideToolbar()
    }
  }

  override fun calculateBounds(): Rectangle {
    val inputBounds = input.calculateBounds()
    val currentOutputs = outputs

    val outputRectangle = currentOutputs?.calculateBounds()?.takeIf { !it.isEmpty }
    val height = outputRectangle?.let { it.height + it.y - inputBounds.y } ?: inputBounds.height

    return Rectangle(0, inputBounds.y, editor.contentSize.width, height)
  }

  fun updateFrameVisibility(selected: Boolean, color: Color): Unit = _controllers.forEach {
    it.updateFrameVisibility(selected, interval, color)
  }

  private fun updateExecutionStatus(executionStatus: ExecutionStatus) {
    input.runCellButton?.updateGutterAction(executionStatus.status)
  }

  fun addDropHighlightIfApplicable(): Unit? =
    _controllers.filterIsInstance<DropHighlightableCellPanel>().firstOrNull()?.addDropHighlight()

  fun removeDropHighlightIfPresent(): Unit? =
    _controllers.filterIsInstance<DropHighlightableCellPanel>().firstOrNull()?.removeDropHighlight()

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
