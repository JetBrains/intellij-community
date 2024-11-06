package com.intellij.notebooks.visualization

import com.intellij.ide.ui.LafManagerListener
import com.intellij.notebooks.ui.isFoldingEnabledKey
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.ui.*
import com.intellij.notebooks.visualization.ui.EditorCellEventListener.*
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewCreated
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.removeUserData
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.concurrency.ThreadingAssertions

class NotebookCellInlayManager private constructor(
  val editor: EditorImpl,
  private val shouldCheckInlayOffsets: Boolean,
  private val inputFactories: List<NotebookCellInlayController.InputFactory>,
  private val cellExtensionFactories: List<CellExtensionFactory>,
) : Disposable, NotebookIntervalPointerFactory.ChangeListener {

  private val notebookCellLines = NotebookCellLines.get(editor)

  private var initialized = false

  private var _cells = mutableListOf<EditorCell>()

  val cells: List<EditorCell> get() = _cells.toList()

  val views = mutableMapOf<EditorCell, EditorCellView>()

  /**
   * Listens for inlay changes (called after all inlays are updated). Feel free to convert it to the EP if you need another listener
   */
  var changedListener: InlaysChangedListener? = null

  private val cellEventListeners = EventDispatcher.create(EditorCellEventListener::class.java)

  private val cellViewEventListeners = EventDispatcher.create(EditorCellViewEventListener::class.java)

  private val invalidationListeners = mutableListOf<Runnable>()

  private var valid = false

  private var updateCtx: UpdateContext? = null

  /*
   EditorImpl sets `myDocumentChangeInProgress` attribute to true during document update processing, that prevents correct update
   of custom folding regions.When this flag is set, folding updates will be postponed until the editor finishes its work.
   */
  private var editorIsProcessingDocument = false

  private var postponedUpdates = mutableListOf<UpdateContext>()

  fun <T> update(force: Boolean = false, block: (updateCtx: UpdateContext) -> T): T {
    val ctx = updateCtx
    return if (ctx != null) {
      block(ctx)
    }
    else {
      val newCtx = UpdateContext(force)
      updateCtx = newCtx
      try {
        val jupyterBoundsChangeHandler = JupyterBoundsChangeHandler.get(editor)
        jupyterBoundsChangeHandler.postponeUpdates()
        val r = keepScrollingPositionWhile(editor) {
          val r = block(newCtx)
          updateCtx = null
          if (editorIsProcessingDocument) {
            postponedUpdates.add(newCtx)
          }
          else {
            newCtx.applyUpdates(editor)
          }
          r
        }
        inlaysChanged()
        jupyterBoundsChangeHandler.boundsChanged()
        jupyterBoundsChangeHandler.performPostponed()
        r
      }
      finally {
        updateCtx = null
      }
    }
  }

  override fun dispose() {
    editor.removeUserData(CELL_INLAY_MANAGER_KEY)
  }

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
    editor.putUserData(CELL_INLAY_MANAGER_KEY, this)

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

    cellEventListeners.addListener(object : EditorCellEventListener {
      override fun onEditorCellEvents(events: List<EditorCellEvent>) {
        updateUI(events)
      }
    })

    editor.document.addDocumentListener(object : BulkAwareDocumentListener.Simple {
      override fun beforeDocumentChange(document: Document) {
        editorIsProcessingDocument = true
      }

      override fun afterDocumentChange(document: Document) {
        editorIsProcessingDocument = false
        postponedUpdates.forEach {
          it.applyUpdates(editor)
        }
        postponedUpdates.clear()
      }
    }, this)

    handleRefreshedDocument()
  }

  private fun updateUI(events: List<EditorCellEvent>) {
    update { ctx ->
      for (event in events) {
        when (event) {
          is CellCreated -> {
            val cell = event.cell
            cell.visible.afterChange { visible ->
              if (visible) {
                createCellViewIfNecessary(cell, ctx)
              }
              else {
                disposeCellView(cell)
              }
            }
            createCellViewIfNecessary(cell, ctx)
          }
          is CellRemoved -> {
            disposeCellView(event.cell)
          }
        }
      }
    }
  }

  private fun createCellViewIfNecessary(cell: EditorCell, ctx: UpdateContext) {
    if (views[cell] == null) {
      createCellView(cell, ctx)
    }
  }

  private fun createCellView(
    cell: EditorCell,
    ctx: UpdateContext,
  ) {
    val view = EditorCellView(editor, notebookCellLines, cell, this)
    Disposer.register(cell, view)
    view.updateCellFolding(ctx)
    views[cell] = view
    fireCellViewCreated(view)
  }

  private fun disposeCellView(cell: EditorCell) {
    views.remove(cell)?.let { view ->
      fireCellViewRemoved(view)
      Disposer.dispose(view)
    }
  }

  private fun setupSelectionUI() {
    editor.caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        updateSelection()
      }
    }, this)
  }

  private fun updateSelection() {
    val selectionModel = editor.cellSelectionModel ?: error("The selection model is supposed to be installed")
    val selectedCells = selectionModel.selectedCells.map { it.ordinal }
    for (cell in cells) {
      cell.selected.set(cell.intervalPointer.get()?.ordinal in selectedCells)

      if (cell.selected.get()) {
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
        val removedRegions = removedRegions.filter { editor.foldingModel.getCollapsedRegionAtOffset(it.startOffset) == null }
        this.removedRegions.clear()
        update { ctx ->
          changedRegions.forEach { region ->
            editorCells(region).forEach {
              it.visible.set(region.isExpanded)
            }
          }
          removedRegions.forEach { region ->
            editorCells(region).forEach {
              it.visible.set(true)
            }
          }
        }
      }
    }, this)
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
  }

  private fun createCell(interval: NotebookIntervalPointer) = EditorCell(editor, this, interval).also {
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
    ): NotebookCellInlayManager {
      EditorEmbeddedComponentContainer(editor as EditorEx)
      val notebookCellInlayManager = NotebookCellInlayManager(
        editor,
        shouldCheckInlayOffsets,
        inputFactories,
        cellExtensionFactories
      ).also { Disposer.register(editor.disposable, it) }
      editor.putUserData(isFoldingEnabledKey, Registry.`is`("jupyter.editor.folding.cells"))
      notebookCellInlayManager.initialize()
      NotebookIntervalPointerFactory.get(editor).changeListeners.addListener(notebookCellInlayManager, notebookCellInlayManager)
      return notebookCellInlayManager
    }

    /** NotebookCellInlayManager exist only on Front in RemoteDev. */
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
    val cell = _cells[index]
    cell.onBeforeRemove()
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
}

class UpdateContext(val force: Boolean = false) {

  private val foldingOperations = mutableListOf<(FoldingModelEx) -> Unit>()

  fun addFoldingOperation(block: (FoldingModelEx) -> Unit) {
    foldingOperations.add(block)
  }

  fun applyUpdates(editor: Editor) {
    if (!editor.isDisposed && foldingOperations.isNotEmpty()) {
      val foldingModel = editor.foldingModel as FoldingModelEx
      foldingModel.runBatchFoldingOperation {
        foldingOperations.forEach { it(foldingModel) }
      }
    }
  }
}