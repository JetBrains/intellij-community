package org.jetbrains.plugins.notebooks.visualization

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NOTEBOOK_EDITOR_MODE
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorModeListener
import org.jetbrains.plugins.notebooks.ui.isFoldingEnabledKey
import org.jetbrains.plugins.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import org.jetbrains.plugins.notebooks.visualization.ui.*
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellEventListener.*
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellViewEventListener.CellViewCreated
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved

class NotebookCellInlayManager private constructor(
  val editor: EditorImpl,
  private val shouldCheckInlayOffsets: Boolean,
  private val inputFactories: List<NotebookCellInlayController.InputFactory>,
  private val cellExtensionFactories: List<CellExtensionFactory>,
  parentScope: CoroutineScope,
) : Disposable, NotebookIntervalPointerFactory.ChangeListener, NotebookEditorModeListener {
  private val coroutineScope = parentScope.childScope("NotebookCellInlayManager")

  private val notebookCellLines = NotebookCellLines.get(editor)

  private var initialized = false

  private var _cells = mutableListOf<EditorCell>()

  val cells: List<EditorCell> get() = _cells.toList()

  /**
   * Listens for inlay changes (called after all inlays are updated). Feel free to convert it to the EP if you need another listener
   */
  var changedListener: InlaysChangedListener? = null

  private val cellEventListeners = EventDispatcher.create(EditorCellEventListener::class.java)

  private val cellViewEventListeners = EventDispatcher.create(EditorCellViewEventListener::class.java)

  private val invalidationListeners = mutableListOf<Runnable>()

  private var valid = false

  private var updateCtx: UpdateContext? = null

  fun <T> update(force: Boolean = false, block: (updateCtx: UpdateContext) -> T): T {
    val ctx = updateCtx
    return if (ctx != null) {
      block(ctx)
    }
    else {
      val newCtx = UpdateContext(force)
      updateCtx = newCtx
      try {
        val r = keepScrollingPositionWhile(editor) {
          val r = block(newCtx)
          newCtx.applyUpdates(editor)
          r
        }
        inlaysChanged()
        r
      }
      finally {
        updateCtx = null
      }
    }
  }

  override fun dispose() {}

  fun getCellForInterval(interval: NotebookCellLines.Interval): EditorCell =
    _cells[interval.ordinal]

  fun updateAllOutputs() {
    update {
      _cells.forEach {
        it.updateOutputs()
      }
    }
  }

  private fun updateAll() {
    if (initialized) {
      updateCells(cells, force = false)
    }
  }

  fun forceUpdateAll() = runInEdt {
    if (initialized) {
      updateCells(cells, force = true)
    }
  }

  private fun update(pointers: Collection<NotebookIntervalPointer>) = runInEdt {
    updateCells(pointers.mapNotNull { it.get()?.ordinal }.sorted().map { cells[it] }, force = false)
  }

  fun update(cell: EditorCell) = runInEdt {
    update(cell.intervalPointer)
  }

  fun update(pointer: NotebookIntervalPointer) = runInEdt {
    update(SmartList(pointer))
  }

  private fun updateCells(cells: List<EditorCell>, force: Boolean = false) {
    update(force) { ctx ->
      cells.forEach {
        it.update(ctx)
      }
      updateCellsFolding(cells)
    }
  }

  private fun addViewportChangeListener() {
    editor.scrollPane.viewport.addChangeListener {
      _cells.forEach {
        it.onViewportChange()
      }
    }
  }

  private fun initialize() {
    // TODO It would be a cool approach to add inlays lazily while scrolling.

    editor.putUserData(CELL_INLAY_MANAGER_KEY, this)

    handleRefreshedDocument()

    val connection = ApplicationManager.getApplication().messageBus.connect(editor.disposable)
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      updateAll()
    })
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      updateAll()
    })

    addViewportChangeListener()

    editor.foldingModel.addListener(object : FoldingListener {
      override fun onFoldProcessingEnd() {
        invalidateCells()
      }
    }, editor.disposable)

    initialized = true

    setupFoldingListener()
    setupSelectionUI()

    ApplicationManager.getApplication().messageBus.connect(this).subscribe(NOTEBOOK_EDITOR_MODE, this)
  }

  private fun setupSelectionUI() {
    editor.caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        updateSelection()
      }
    })
  }

  private fun updateSelection() {
    val selectionModel = editor.cellSelectionModel ?: error("The selection model is supposed to be installed")
    val selectedCells = selectionModel.selectedCells.map { it.ordinal }
    for (cell in cells) {
      cell.selected = cell.intervalPointer.get()?.ordinal in selectedCells

      if (cell.selected) {
        editor.project?.messageBus?.syncPublisher(JupyterCellSelectionNotifier.TOPIC)?.cellSelected(cell.interval, editor)
      }
    }
  }

  private fun setupFoldingListener() {
    val foldingModel = editor.foldingModel
    foldingModel.addListener(object : FoldingListener {

      val changedRegions = LinkedHashSet<FoldRegion>()
      val removedRegions = LinkedHashSet<FoldRegion>()

      override fun beforeFoldRegionDisposed(region: FoldRegion) {
        removedRegions.add(region)
        changedRegions.remove(region)
      }

      override fun beforeFoldRegionRemoved(region: FoldRegion) {
        removedRegions.add(region)
        changedRegions.remove(region)
      }

      override fun onFoldRegionStateChange(region: FoldRegion) {
        changedRegions.add(region)
      }

      override fun onFoldProcessingEnd() {
        val changedRegions = changedRegions.filter { it.getUserData(FOLDING_MARKER_KEY) == true }
        this.changedRegions.clear()
        val removedRegions = removedRegions.toList()
        this.removedRegions.clear()
        update { ctx ->
          changedRegions.forEach { region ->
            editorCells(region).forEach {
              it.visible = region.isExpanded
            }
          }
          removedRegions.forEach { region ->
            editorCells(region).forEach {
              it.visible = true
            }
          }
        }
      }
    }, editor.disposable)
  }

  private fun editorCells(region: FoldRegion): List<EditorCell> = _cells.filter { cell ->
    val startOffset = editor.document.getLineStartOffset(cell.intervalPointer.get()!!.lines.first)
    val endOffset = editor.document.getLineEndOffset(cell.intervalPointer.get()!!.lines.last)
    startOffset >= region.startOffset && endOffset <= region.endOffset
  }

  private fun handleRefreshedDocument() {
    ThreadingAssertions.softAssertReadAccess()
    _cells.forEach {
      Disposer.dispose(it)
    }
    val pointerFactory = NotebookIntervalPointerFactory.get(editor)

    update {
      _cells = notebookCellLines.intervals.map { interval ->
        createCell(pointerFactory.create(interval))
      }.toMutableList()
    }
    cellEventListeners.multicaster.onEditorCellEvents(_cells.map { CellCreated(it) })
    _cells.forEach {
      it.initView()
    }

    JupyterBoundsChangeHandler.get(editor)?.postponeUpdates()
    _cells.forEach {
      it.view?.postInitInlays()
    }

    inlaysChanged()
    JupyterBoundsChangeHandler.get(editor)?.performPostponed()
  }

  private fun createCell(interval: NotebookIntervalPointer) = EditorCell(editor, this, interval, coroutineScope) { cell ->
    EditorCellView(editor, notebookCellLines, cell, this).also { Disposer.register(cell, it) }
  }.also {
    cellExtensionFactories.forEach { factory ->
      factory.onCellCreated(it)
    }
    Disposer.register(this, it)
  }

  private fun inlaysChanged() {
    changedListener?.inlaysChanged()
  }

  private fun updateCellsFolding(editorCells: List<EditorCell>) = update { updateContext ->
    editorCells.forEach { cell ->
      cell.view?.updateCellFolding(updateContext)
    }
  }

  companion object {
    fun install(
      editor: EditorImpl,
      shouldCheckInlayOffsets: Boolean,
      inputFactories: List<NotebookCellInlayController.InputFactory> = listOf(),
      cellExtensionFactories: List<CellExtensionFactory> = listOf(),
      parentScope: CoroutineScope,
    ) {
      EditorEmbeddedComponentContainer(editor as EditorEx)
      val notebookCellInlayManager = NotebookCellInlayManager(
        editor,
        shouldCheckInlayOffsets,
        inputFactories,
        cellExtensionFactories,
        parentScope
      ).also { Disposer.register(editor.disposable, it) }
      editor.putUserData(isFoldingEnabledKey, Registry.`is`("jupyter.editor.folding.cells"))
      NotebookIntervalPointerFactory.get(editor).changeListeners.addListener(notebookCellInlayManager, editor.disposable)
      notebookCellInlayManager.initialize()
    }

    fun get(editor: Editor): NotebookCellInlayManager? {
      return CELL_INLAY_MANAGER_KEY.get(editor)
    }

    val FOLDING_MARKER_KEY = Key<Boolean>("jupyter.folding.paragraph")
    private val CELL_INLAY_MANAGER_KEY = Key.create<NotebookCellInlayManager>(NotebookCellInlayManager::class.java.name)
  }

  override fun onUpdated(event: NotebookIntervalPointersEvent) = update { ctx ->
    val events = mutableListOf<EditorCellEvent>()
    for (change in event.changes) {
      when (change) {
        is NotebookIntervalPointersEvent.OnEdited -> {
          val cell = _cells[change.intervalAfter.ordinal]
          cell.updateInput()
          events.add(CellUpdated(cell))
        }
        is NotebookIntervalPointersEvent.OnInserted -> {
          change.subsequentPointers.forEach {
            val editorCell = createCell(it.pointer)
            addCell(it.interval.ordinal, editorCell, events)
          }
        }
        is NotebookIntervalPointersEvent.OnRemoved -> {
          change.subsequentPointers.reversed().forEach {
            val index = it.interval.ordinal
            removeCell(index, events)
          }
        }
        is NotebookIntervalPointersEvent.OnSwapped -> {
          val firstCell = _cells[change.firstOrdinal]
          val first = firstCell.intervalPointer
          val secondCell = _cells[change.secondOrdinal]
          firstCell.intervalPointer = secondCell.intervalPointer
          secondCell.intervalPointer = first
          firstCell.update(ctx)
          secondCell.update(ctx)
        }
      }
    }
    event.changes.filterIsInstance<NotebookIntervalPointersEvent.OnInserted>().forEach { change ->
      fixInlaysOffsetsAfterNewCellInsert(change, ctx)
    }
    cellEventListeners.multicaster.onEditorCellEvents(events)

    events.filterIsInstance<CellCreated>().forEach { it.cell.initView() }

    checkInlayOffsets()
  }

  private fun checkInlayOffsets() {
    if (!shouldCheckInlayOffsets) return

    val inlaysOffsets = buildSet {
      for (cell in _cells) {
        add(editor.document.getLineStartOffset(cell.interval.lines.first))
        add(editor.document.getLineEndOffset(cell.interval.lines.last))
      }
    }

    val wronglyPlacedInlays = _cells.asSequence()
      .mapNotNull { it.view }
      .flatMap { it.getInlays() }
      .filter { it.offset !in inlaysOffsets }
      .toSet()
    if (wronglyPlacedInlays.isNotEmpty()) {
      thisLogger().error("Expected offsets: $inlaysOffsets. Wrongly placed offsets: ${wronglyPlacedInlays.map { it.offset }} of inlays $wronglyPlacedInlays, for file = '${editor.virtualFile?.name}'")
    }
  }

  private fun fixInlaysOffsetsAfterNewCellInsert(change: NotebookIntervalPointersEvent.OnInserted, ctx: UpdateContext) {
    val prevCellIndex = change.subsequentPointers.first().interval.ordinal - 1
    if (prevCellIndex >= 0) {
      val prevCell = getCell(prevCellIndex)
      prevCell.update(ctx)
    }
  }

  private fun addCell(index: Int, editorCell: EditorCell, events: MutableList<EditorCellEvent>) {
    _cells.add(index, editorCell)
    events.add(CellCreated(editorCell))
    invalidateCells()
  }

  private fun removeCell(index: Int, events: MutableList<EditorCellEvent>) {
    val removed = _cells.removeAt(index)
    Disposer.dispose(removed)
    events.add(CellRemoved(removed))
    invalidateCells()
  }

  fun addCellEventsListener(editorCellEventListener: EditorCellEventListener, disposable: Disposable) {
    cellEventListeners.addListener(editorCellEventListener, disposable)
  }

  fun addCellViewEventsListener(editorCellViewEventListener: EditorCellViewEventListener, disposable: Disposable) {
    cellViewEventListeners.addListener(editorCellViewEventListener, disposable)
  }

  internal fun fireCellViewCreated(cellView: EditorCellView) {
    cellViewEventListeners.multicaster.onEditorCellViewEvents(listOf(CellViewCreated(cellView)))
  }

  internal fun fireCellViewRemoved(cellView: EditorCellView) {
    cellViewEventListeners.multicaster.onEditorCellViewEvents(listOf(CellViewRemoved(cellView)))
  }

  fun getCell(index: Int): EditorCell {
    return cells[index]
  }

  fun getCell(pointer: NotebookIntervalPointer): EditorCell {
    return getCell(pointer.get()!!.ordinal)
  }

  fun invalidateCells() {
    if (valid) {
      valid = false
      invalidationListeners.forEach { it.run() }
    }
  }

  internal fun getInputFactories(): Sequence<NotebookCellInlayController.InputFactory> {
    return inputFactories.asSequence()
  }

  override fun onModeChange(editor: Editor, mode: NotebookEditorMode) {
    if (editor == this.editor) {
      when (mode) {
        NotebookEditorMode.EDIT -> {
          editor.caretModel.allCarets.forEach { caret ->
            getCellByLine(editor.document.getLineNumber(caret.offset))?.switchToEditMode()
          }
        }
        NotebookEditorMode.COMMAND -> {
          _cells.forEach {
            it.switchToCommandMode()
          }
        }
      }
    }
  }

  fun getCellByLine(line: Int): EditorCell? {
    return _cells.firstOrNull { it.interval.lines.contains(line) }
  }

}

class UpdateContext(val force: Boolean = false) {

  private val foldingOperations = mutableListOf<() -> Unit>()

  fun addFoldingOperation(block: () -> Unit) {
    foldingOperations.add(block)
  }

  fun applyUpdates(editor: Editor) {
    if (foldingOperations.isNotEmpty()) {
      editor.foldingModel.runBatchFoldingOperation {
        foldingOperations.forEach { it() }
      }
    }
  }
}
