package com.intellij.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.*
import com.intellij.notebooks.visualization.NotebookCellInlayController.InputFactory
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedControllerFactory
import com.intellij.notebooks.visualization.ui.cellsDnD.DropHighlightable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.asSafely
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.reflect.KClass


class EditorCellView(
  val editor: EditorImpl,
  private val intervals: NotebookCellLines,
  val cell: EditorCell,
  private val cellInlayManager: NotebookCellInlayManager,
) : EditorCellViewComponent(), Disposable {
  var controllers: List<NotebookCellInlayController> = emptyList()
    private set

  private val intervalPointer: NotebookIntervalPointer
    get() = cell.intervalPointer

  val interval: NotebookCellLines.Interval
    get() = intervalPointer.get() ?: error("Invalid interval")


  private val cellHighlighters = mutableListOf<RangeHighlighter>()

  val input: EditorCellInput = createEditorCellInput()

  var outputs: EditorCellOutputsView? = null
    private set


  val selected: Boolean
    get() = cell.isSelected.get()


  private val mouseOver
    get() = cell.isHovered.get()

  // We are storing last lines range for highlighters to prevent highlighters unnecessary recreation on the same lines.
  private var lastHighLightersLines: IntRange? = null

  var isUnderDiff: Boolean
    get() = cell.isUnderDiff.get()
    set(value) = cell.isUnderDiff.set(value)

  val selfManagedControllers: List<SelfManagedCellController> by lazy {
    SelfManagedControllerFactory.createControllers(this)
  }

  init {
    cell.source.bind(this) {
      updateInput()
    }
    cell.isSelected.bind(this) { selected ->
      updateSelected()
    }
    editor.notebookAppearance.codeCellBackgroundColor.bind(this) { backgroundColor ->
      updateCellHighlight(force = true)
    }
    cell.notebook.showCellToolbar.bind(this) {
    }
    cell.isHovered.bind(this) {
      updateHovered()
    }
    updateSelfManaged()
    recreateControllers()
    updateOutputs()
    updateControllers()
  }

  private fun updateSelected() {
    updateFolding()
    updateCellHighlight()
  }

  override fun dispose() {
    super.dispose()
    controllers.forEach { controller ->
      disposeController(controller)
    }
    removeCellHighlight()
  }

  private fun createEditorCellInput(): EditorCellInput {
    val inputFactory = getInputFactories().firstOrNull { it.supports(editor, cell) } ?: fallbackInputFactory
    return EditorCellInput(inputFactory, cell).also {
      add(it)
    }
  }

  private fun disposeController(controller: NotebookCellInlayController) {
    val inlay = controller.inlay
    inlay.renderer.asSafely<JComponent>()?.let { DataManager.removeDataProvider(it) }
    Disposer.dispose(inlay)
  }

  fun update(updateContext: UpdateContext) {
    input.updateInput()
    updateSelfManaged()
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

  private fun recreateControllers() {
    editor.updateManager.update { updateContext ->
      updateContext.addInlayOperation {
        val otherFactories = NotebookCellInlayController.Factory.EP_NAME.extensionList
          .filter { it !is InputFactory }
        val controllersToDispose = controllers.toMutableSet()
        controllers = if (!editor.isDisposed) {
          otherFactories.mapNotNull { factory ->
            val intervalIterator = intervals.intervals.listIterator(interval.ordinal)
            val cellInlayController = failSafeCompute(factory, editor, controllers, intervalIterator)
            if (cellInlayController is Disposable) {
              Disposer.register(this, cellInlayController)
            }
            cellInlayController
          }
        }
        else {
          emptyList()
        }
        controllersToDispose.removeAll(controllers.toSet())
        controllersToDispose.forEach { disposeController(it) }
        updateControllers()
      }
    }
  }

  private fun updateSelfManaged() {
    selfManagedControllers.forEach {
      it.selfUpdate()
    }
  }

  private fun updateInput() = runInEdt {
    updateCellHighlight()
    input.updateInput()
    checkAndRebuildInlays()
  }

  override fun doCheckAndRebuildInlays() {
    updateSelfManaged()
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

  fun updateHovered() {
    updateFolding()
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

    if (!force && interval.lines == lastHighLightersLines) {
      return
    }
    lastHighLightersLines = IntRange(interval.lines.first, interval.lines.last)
    updateSelfManaged()

    removeCellHighlight()

    for (controller: NotebookCellInlayController in controllers) {
      controller.createGutterRendererLineMarker(editor, interval, this)
    }
  }


  private fun updateFolding() {
    input.folding.visible = mouseOver || selected
    input.folding.selected = selected
    outputs?.foldingsVisible = mouseOver || selected
    outputs?.foldingsSelected = selected
  }


  override fun calculateBounds(): Rectangle {
    val inputBounds = input.calculateBounds()
    val currentOutputs = outputs

    val outputRectangle = currentOutputs?.calculateBounds()?.takeIf { !it.isEmpty }
    val height = outputRectangle?.let { it.height + it.y - inputBounds.y } ?: inputBounds.height

    return Rectangle(0, inputBounds.y, editor.contentSize.width, height)
  }

  fun updateFrameVisibility(selected: Boolean, color: Color) {
    val interval = cell.intervalOrNull ?: return
    controllers.forEach {
      it.updateFrameVisibility(selected, interval, color)
    }
  }

  fun addDropHighlightIfApplicable() {
    selfManagedControllers.filterIsInstance<DropHighlightable>().firstOrNull()?.addDropHighlight()
  }

  fun removeDropHighlightIfPresent() {
    selfManagedControllers.filterIsInstance<DropHighlightable>().firstOrNull()?.removeDropHighlight()
  }


  override fun doGetInlays(): Sequence<Inlay<*>> {
    return controllers.map { it.inlay }.asSequence()
  }

  fun requestCaret() {
    input.requestCaret()
  }

  companion object {
    private val fallbackInputFactory = object : InputFactory {
      override fun createComponent(editor: EditorImpl, cell: EditorCell) = TextEditorCellViewComponent(cell)
      override fun supports(editor: EditorImpl, cell: EditorCell) = true
    }
  }
}