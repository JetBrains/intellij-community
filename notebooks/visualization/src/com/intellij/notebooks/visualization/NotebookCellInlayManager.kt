package com.intellij.notebooks.visualization

import com.intellij.ide.ui.LafManagerListener
import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.visualization.ui.*
import com.intellij.notebooks.visualization.ui.EditorCellEventListener.*
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewCreated
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import com.intellij.notebooks.visualization.ui.endInlay.EditorNotebookEndInlay
import com.intellij.notebooks.visualization.ui.endInlay.EditorNotebookEndInlayProvider
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
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.concurrency.ThreadingAssertions
import java.awt.Point

class NotebookCellInlayManager private constructor(
  val editor: EditorImpl,
  private val shouldCheckInlayOffsets: Boolean,
  val notebook: EditorNotebook,
) : Disposable, NotebookIntervalPointerFactory.ChangeListener {

  private val notebookCellLines = NotebookCellLines.get(editor)

  private var initialized = false

  val cells: List<EditorCell> get() = notebook.cells

  val endNotebookInlays: List<EditorNotebookEndInlay> = EditorNotebookEndInlayProvider.create(this)

  internal val views: MutableMap<EditorCell, EditorCellView> = mutableMapOf()

  private val cellViewEventListeners = EventDispatcher.create(EditorCellViewEventListener::class.java)

  private fun update(force: Boolean = false, keepScrollingPosition: Boolean = false, block: (UpdateContext) -> Unit) {
    editor.updateManager.update(force = force, keepScrollingPositon = keepScrollingPosition, block = block)
  }

  override fun dispose() {
    views.clear()
    editor.removeUserData(CELL_INLAY_MANAGER_KEY)
  }

  fun getCellForInterval(interval: NotebookCellLines.Interval): EditorCell =
    notebook.cells[interval.ordinal]

  fun updateAllOutputs() {
    update {
      notebook.cells.forEach {
        it.updateOutputs()
      }
    }
  }

  private fun updateAll() {
    if (initialized) {
      updateCells(cells, force = false)
    }
  }

  fun forceUpdateAll(): Unit = runInEdt {
    if (initialized) {
      updateCells(cells, force = true)
    }
  }

  private fun update(pointers: Collection<NotebookIntervalPointer>) = runInEdt {
    updateCells(pointers.mapNotNull { it.get()?.ordinal }.sorted().map { cells[it] }, force = false)
  }

  fun update(cell: EditorCell): Unit = runInEdt {
    update(cell.intervalPointer)
  }

  fun update(pointer: NotebookIntervalPointer): Unit = runInEdt {
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
      notebook.cells.forEach {
        it.onViewportChange()
      }
    }
  }

  fun initialize() {
    editor.putUserData(CELL_INLAY_MANAGER_KEY, this)

    val connection = ApplicationManager.getApplication().messageBus.connect(editor.disposable)
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      updateAll()
    })
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      updateAll()
    })

    addViewportChangeListener()

    initialized = true

    setupFoldingListener()
    setupSelectionUI()


    notebook.addCellEventsListener(this, object : EditorCellEventListener {
      override fun onEditorCellEvents(events: List<EditorCellEvent>) {
        updateUI(events)
      }
    })

    handleRefreshedDocument()
  }

  fun getCellByPoint(point: Point): EditorCell? {
    val visualLine = editor.xyToLogicalPosition(point)
    val cur = cells.firstOrNull { it.interval.lines.contains(visualLine.line) }
    return cur

  }
  private fun updateUI(events: List<EditorCellEvent>) {
    update {
      for (event in events) {
        when (event) {
          is CellCreated -> {
            val cell = event.cell
            cell.isUnfolded.bind(cell) { visible ->
              updateCellVisibility(cell, visible)
            }
          }
          is CellRemoved -> {
            disposeCellView(event.cell)
          }
        }
      }
    }
  }

  private fun updateCellVisibility(cell: EditorCell, visible: Boolean) = update { ctx ->
    if (visible) {
      createCellViewIfNecessary(cell, ctx)
    }
    else {
      disposeCellView(cell)
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
      cell.isSelected.set(cell.intervalPointer.get()?.ordinal in selectedCells)

      if (cell.isSelected.get()) {
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
              it.isUnfolded.set(region.isExpanded)
            }
          }
          removedRegions.forEach { region ->
            editorCells(region).forEach {
              it.isUnfolded.set(true)
            }
          }
        }
      }
    }, this)
  }

  private fun editorCells(region: FoldRegion): List<EditorCell> = notebook.cells.filter { cell ->
    val startOffset = editor.document.getLineStartOffset(cell.intervalPointer.get()!!.lines.first)
    val endOffset = editor.document.getLineEndOffset(cell.intervalPointer.get()!!.lines.last)
    startOffset >= region.startOffset && endOffset <= region.endOffset
  }

  private fun handleRefreshedDocument() {
    ThreadingAssertions.softAssertReadAccess()
    notebook.clear()
    val pointerFactory = NotebookIntervalPointerFactory.get(editor)

    update(keepScrollingPosition = false) {
      notebookCellLines.intervals.forEach { interval ->
        notebook.addCell(pointerFactory.create(interval))
      }
    }
    //Forcefully synchronize components and inlays height
    update(keepScrollingPosition = false) {
      editor.contentComponent.components
        .filterIsInstance<EditorEmbeddedComponentManager.FullEditorWidthRenderer>()
        .forEach { it.doLayout() }
    }
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
      editorNotebookPostprocessors: List<EditorNotebookPostprocessor> = listOf(),
    ): NotebookCellInlayManager {
      EditorEmbeddedComponentContainer(editor as EditorEx)
      val updateManager = UpdateManager(editor)
      Disposer.register(editor.disposable, updateManager)
      val notebook = createNotebook(editor, editorNotebookPostprocessors)
      val notebookCellInlayManager = NotebookCellInlayManager(
        editor,
        shouldCheckInlayOffsets,
        notebook
      ).also { Disposer.register(editor.disposable, it) }

      NotebookIntervalPointerFactory.get(editor).changeListeners.addListener(notebookCellInlayManager, notebookCellInlayManager)
      return notebookCellInlayManager
    }

    private fun createNotebook(
      editor: EditorImpl,
      editorNotebookPostprocessors: List<EditorNotebookPostprocessor>,
    ): EditorNotebook {
      val notebook = EditorNotebook(editor)
      editorNotebookPostprocessors.forEach {
        it.postprocess(notebook)
      }
      Disposer.register(editor.disposable, notebook)
      return notebook
    }

    /** NotebookCellInlayManager exist only on Front in RemoteDev. */
    fun get(editor: Editor): NotebookCellInlayManager? {
      return CELL_INLAY_MANAGER_KEY.get(editor)
    }

    val FOLDING_MARKER_KEY: Key<Boolean> = Key<Boolean>("jupyter.folding.paragraph")
    private val CELL_INLAY_MANAGER_KEY = Key.create<NotebookCellInlayManager>(NotebookCellInlayManager::class.java.name)
  }

  private val currentEventsQueue = mutableListOf<NotebookIntervalPointersEvent>()

  override fun onUpdated(event: NotebookIntervalPointersEvent) {
    currentEventsQueue.add(event)
    if (!event.isInBulkUpdate) {
      bulkUpdateFinished()
    }
  }

  override fun bulkUpdateFinished(): Unit = update { ctx ->
    val events = currentEventsQueue.toList()
    currentEventsQueue.clear()
    for (event in events) {
      for (change in event.changes) {
        when (change) {
          is NotebookIntervalPointersEvent.OnEdited -> {
            val cell = notebook.cells[change.intervalAfter.ordinal]
            cell.updateInput()
          }
          is NotebookIntervalPointersEvent.OnInserted -> {
            change.subsequentPointers.forEach {
              addCell(it.pointer)
            }
          }
          is NotebookIntervalPointersEvent.OnRemoved -> {
            change.subsequentPointers.reversed().forEach {
              val index = it.interval.ordinal
              removeCell(index)
            }
          }
          is NotebookIntervalPointersEvent.OnSwapped -> {
            val firstCell = notebook.cells[change.firstOrdinal]
            val first = firstCell.intervalPointer
            val secondCell = notebook.cells[change.secondOrdinal]
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
    }

    checkInlayOffsets()
  }

  private fun checkInlayOffsets() {
    if (!shouldCheckInlayOffsets) return

    val inlaysOffsets = buildSet {
      for (cell in notebook.cells) {
        add(editor.document.getLineStartOffset(cell.interval.lines.first))
        add(editor.document.getLineEndOffset(cell.interval.lines.last))
      }
    }

    val wronglyPlacedInlays = notebook.cells.asSequence()
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

  private fun addCell(pointer: NotebookIntervalPointer) {
    notebook.addCell(pointer)
  }

  private fun removeCell(index: Int) {
    notebook.removeCell(index)
  }

  fun addCellEventsListener(editorCellEventListener: EditorCellEventListener, disposable: Disposable) {
    notebook.addCellEventsListener(disposable, editorCellEventListener)
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

  fun getCell(interval: NotebookCellLines.Interval): EditorCell {
    return cells[interval.ordinal]
  }

  fun getCellOrNull(index: Int): EditorCell? {
    return cells.getOrNull(index)
  }

  fun getCellOrNull(interval: NotebookCellLines.Interval): EditorCell? {
    return cells.getOrNull(interval.ordinal)
  }

  fun getCell(pointer: NotebookIntervalPointer): EditorCell {
    return getCell(pointer.get()!!)
  }

  internal fun getInputFactories(): Sequence<NotebookCellInlayController.InputFactory> {
    return NotebookCellInlayController.InputFactory.EP_NAME.extensionList.asSequence()
  }
}