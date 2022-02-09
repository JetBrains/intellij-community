package org.jetbrains.plugins.notebooks.visualization

import com.intellij.ide.DataManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.Processor
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

class NotebookCellInlayManager private constructor(val editor: EditorImpl) {
  private val inlays: MutableMap<Inlay<*>, NotebookCellInlayController> = HashMap()
  private val notebookCellLines = NotebookCellLines.get(editor)
  private val viewportQueue = MergingUpdateQueue("NotebookCellInlayManager Viewport Update", 100, true, null, editor.disposable, null, true)

  /** 20 is 1000 / 50, two times faster than the eye refresh rate. Actually, the value has been chosen randomly, without experiments. */
  private val updateQueue = MergingUpdateQueue("NotebookCellInlayManager Interval Update", 20, true, null, editor.disposable, null, true)
  private var initialized = false

  /**
   * Listens for inlay changes (called after all inlays are updated). Feel free to convert it to the EP if you need another listener
   */
  var changedListener: InlaysChangedListener? = null

  fun inlaysForInterval(interval: NotebookCellLines.Interval): Iterable<NotebookCellInlayController> =
    getMatchingInlaysForLines(interval.lines)

  /** It's public, but think twice before using it. Called many times in a row, it can freeze UI. Consider using [update] instead. */
  fun updateImmediately(lines: IntRange) {
    if (initialized) {
      updateConsequentInlays(lines)
    }
  }

  /** It's public, but think seven times before using it. Called many times in a row, it can freeze UI. */
  fun updateAllImmediately() {
    if (initialized) {
      updateQueue.cancelAllUpdates()
      updateConsequentInlays(0..editor.document.lineCount)
    }
  }

  fun update(lines: IntRange) {
    // TODO Hypothetically, there can be a race between cell addition/deletion and updating of old cells.
    updateQueue.queue(UpdateInlaysTask(this, lines))
  }

  private fun addViewportChangeListener() {
    editor.scrollPane.viewport.addChangeListener {
      viewportQueue.queue(object : Update("Viewport change") {
        override fun run() {
          if (editor.isDisposed) return
          for ((inlay, controller) in inlays) {
            controller.onViewportChange()

            // Many UI instances has overridden getPreferredSize relying on editor dimensions.
            inlay.renderer?.castSafelyTo<JComponent>()?.updateUI()
          }
        }
      })
    }
  }

  private fun initialize() {
    // TODO It would be a cool approach to add inlays lazily while scrolling.

    editor.putUserData(key, this)

    handleRefreshedDocument()

    addDocumentListener()

    val appMessageBus = ApplicationManager.getApplication().messageBus.connect(editor.disposable)

    appMessageBus.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      refreshHighlightersLookAndFeel()
    })
    appMessageBus.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      refreshHighlightersLookAndFeel()
    })

    addViewportChangeListener()

    initialized = true
  }

  private fun refreshHighlightersLookAndFeel() {
    for (highlighter in editor.markupModel.allHighlighters) {
      if (highlighter.customRenderer === NotebookCellHighlighterRenderer) {
        (highlighter as? RangeHighlighterEx)?.setTextAttributes(textAttributesForHighlighter())
      }
    }
  }

  private fun handleRefreshedDocument() {
    val factories = NotebookCellInlayController.Factory.EP_NAME.extensionList
    for (interval in notebookCellLines.intervals) {
      for (factory in factories) {
        val controller = factory.compute(editor, emptyList(), notebookCellLines.intervals.listIterator(interval.ordinal))
        if (controller != null) {
          rememberController(controller, interval)
        }
      }
    }
    addHighlighters(notebookCellLines.intervals)
    inlaysChanged()
  }

  private fun addDocumentListener() {
    val documentListener = object : DocumentListener {
      private var matchingCellsBeforeChange: List<NotebookCellLines.Interval> = emptyList()
      private var isBulkModeEnabled = false;

      private fun interestingLogicalLines(document: Document, startOffset: Int, length: Int): IntRange {
        // Adding one additional line is needed to handle deletions at the end of the document.
        val end =
          if (startOffset + length <= document.textLength) document.getLineNumber(startOffset + length)
          else document.lineCount + 1
        return document.getLineNumber(startOffset)..end
      }

      override fun bulkUpdateStarting(document: Document) {
        isBulkModeEnabled = true
        matchingCellsBeforeChange = notebookCellLines.getMatchingCells(0 until document.lineCount)
      }

      override fun beforeDocumentChange(event: DocumentEvent) {
        if (isBulkModeEnabled) return
        val document = event.document
        val logicalLines = interestingLogicalLines(document, event.offset, event.oldLength)

        matchingCellsBeforeChange = notebookCellLines.getMatchingCells(logicalLines)
      }

      override fun documentChanged(event: DocumentEvent) {
        if (isBulkModeEnabled) return
        val logicalLines = interestingLogicalLines(event.document, event.offset, event.newLength)
        ensureInlaysAndHighlightersExist(matchingCellsBeforeChange, logicalLines)
      }

      override fun bulkUpdateFinished(document: Document) {
        isBulkModeEnabled = false
        // bulk mode is over, now we could access inlays, let's update them all
        ensureInlaysAndHighlightersExist(matchingCellsBeforeChange, 0 until document.lineCount)
      }
    }

    editor.document.addDocumentListener(documentListener, editor.disposable)
  }

  private fun ensureInlaysAndHighlightersExist(matchingCellsBeforeChange: List<NotebookCellLines.Interval>, logicalLines: IntRange) {
    val interestingRange =
      matchingCellsBeforeChange
        .map { it.lines }
        .takeIf { it.isNotEmpty() }
        ?.let { min(logicalLines.first, it.first().first)..max(it.last().last, logicalLines.last) }
      ?: logicalLines
    updateConsequentInlays(interestingRange)
  }

  private fun inlaysChanged() {
    changedListener?.inlaysChanged()
  }

  private fun updateConsequentInlays(interestingRange: IntRange) {
    editor.notebookCellEditorScrollingPositionKeeper?.saveSelectedCellPosition()
    val matchingIntervals = notebookCellLines.getMatchingCells(interestingRange)
    val fullInterestingRange =
      if (matchingIntervals.isNotEmpty()) matchingIntervals.first().lines.first..matchingIntervals.last().lines.last
      else interestingRange

    val existingHighlighters = getMatchingHighlightersForLines(fullInterestingRange)
    val intervalsToAddHighlightersFor = matchingIntervals.associateByTo(HashMap()) { it.lines }
    for (highlighter in existingHighlighters) {
      val lines = editor.document.run { getLineNumber(highlighter.startOffset)..getLineNumber(highlighter.endOffset) }
      if (intervalsToAddHighlightersFor.remove(lines)?.shouldHaveHighlighter != true) {
        editor.markupModel.removeHighlighter(highlighter)
      }
    }
    addHighlighters(intervalsToAddHighlightersFor.values)

    val allMatchingInlays: MutableList<Pair<Int, NotebookCellInlayController>> =
      getMatchingInlaysForLines(fullInterestingRange)
        .mapTo(mutableListOf()) {
          editor.document.getLineNumber(it.inlay.offset) to it
        }
    val allFactories = NotebookCellInlayController.Factory.EP_NAME.extensionList

    for (interval in matchingIntervals) {
      val seenControllersByFactory: Map<NotebookCellInlayController.Factory, MutableList<NotebookCellInlayController>> =
        allFactories.associateWith { SmartList<NotebookCellInlayController>() }
      allMatchingInlays.removeIf { (inlayLine, controller) ->
        if (inlayLine in interval.lines) {
          seenControllersByFactory[controller.factory]?.add(controller)
          true
        }
        else false
      }
      for ((factory, controllers) in seenControllersByFactory) {
        val actualController = if (!editor.isDisposed) {
          factory.compute(editor, controllers, notebookCellLines.intervals.listIterator(interval.ordinal))
        }
        else {
          null
        }
        if (actualController != null) {
          rememberController(actualController, interval)
        }
        for (oldController in controllers) {
          if (oldController != actualController) {
            Disposer.dispose(oldController.inlay, false)
          }
        }
      }
    }

    for ((_, controller) in allMatchingInlays) {
      Disposer.dispose(controller.inlay, false)
    }
    inlaysChanged()
  }

  private fun rememberController(controller: NotebookCellInlayController, interval: NotebookCellLines.Interval) {
    val inlay = controller.inlay
    inlay.renderer.castSafelyTo<JComponent>()?.let { component ->
      component.putClientProperty(
        DataManager.CLIENT_PROPERTY_DATA_PROVIDER,
        DataProvider { key ->
          when (key) {
            NOTEBOOK_CELL_LINES_INTERVAL_DATA_KEY.name -> interval
            PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> component
            PlatformDataKeys.EDITOR.name -> editor
            else -> null
          }
        },
      )
    }
    if (inlays.put(inlay, controller) !== controller) {
      val disposable = Disposable {
        inlay.renderer.castSafelyTo<JComponent>()?.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, null)
        inlays.remove(inlay)
      }
      if (Disposer.isDisposed(inlay)) {
        @Suppress("SSBasedInspection")
        disposable.dispose()
      } else {
        Disposer.register(inlay, disposable)
      }
    }
  }

  private fun getMatchingHighlightersForLines(lines: IntRange): List<RangeHighlighterEx> =
    mutableListOf<RangeHighlighterEx>()
      .also { list ->
        val startOffset = editor.document.getLineStartOffset(saturateLine(lines.first))
        val endOffset = editor.document.getLineEndOffset(saturateLine(lines.last))
        editor.markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, Processor {
          if (it.customRenderer === NotebookCellHighlighterRenderer) {
            list.add(it)
          }
          true
        })
      }

  private fun getMatchingInlaysForLines(lines: IntRange): List<NotebookCellInlayController> =
    getMatchingInlaysForOffsets(
      editor.document.getLineStartOffset(saturateLine(lines.first)),
      editor.document.getLineEndOffset(saturateLine(lines.last)))

  private fun saturateLine(line: Int): Int =
    line.coerceAtMost(editor.document.lineCount - 1).coerceAtLeast(0)

  private fun getMatchingInlaysForOffsets(startOffset: Int, endOffset: Int): List<NotebookCellInlayController> =
    editor.inlayModel
      .getBlockElementsInRange(startOffset, endOffset)
      .mapNotNull(inlays::get)

  private val NotebookCellLines.Interval.shouldHaveHighlighter: Boolean
    get() = type == NotebookCellLines.CellType.CODE

  private fun addHighlighters(intervals: Collection<NotebookCellLines.Interval>) {
    val document = editor.document
    for (interval in intervals) {
      if (interval.shouldHaveHighlighter) {
        val highlighter = editor.markupModel.addRangeHighlighter(
          document.getLineStartOffset(interval.lines.first),
          document.getLineEndOffset(interval.lines.last),
          // Code cell background should be seen behind any syntax highlighting, selection or any other effect.
          HighlighterLayer.FIRST - 100,
          textAttributesForHighlighter(),
          HighlighterTargetArea.LINES_IN_RANGE
        )
        highlighter.customRenderer = NotebookCellHighlighterRenderer
      }
    }
  }

  private fun textAttributesForHighlighter() = TextAttributes().apply {
    backgroundColor = editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme)
  }

  private fun NotebookCellLines.getMatchingCells(logicalLines: IntRange): List<NotebookCellLines.Interval> =
    mutableListOf<NotebookCellLines.Interval>().also { result ->
      // Since inlay appearance may depend from neighbour cells, adding one more cell at the start and at the end.
      val iterator = intervalsIterator(logicalLines.first)
      if (iterator.hasPrevious()) iterator.previous()
      for (interval in iterator) {
        result.add(interval)
        if (interval.lines.first > logicalLines.last) break
      }
    }

  @TestOnly
  fun getInlays(): MutableMap<Inlay<*>, NotebookCellInlayController> = inlays

  @TestOnly
  fun updateControllers(matchingCells: List<NotebookCellLines.Interval>, logicalLines: IntRange) {
    ensureInlaysAndHighlightersExist(matchingCells, logicalLines)
  }

  companion object {
    private val LOG = logger<NotebookCellInlayManager>()

    @JvmStatic
    fun install(editor: EditorImpl) {
      NotebookCellInlayManager(editor).initialize()
    }

    @JvmStatic
    fun get(editor: Editor): NotebookCellInlayManager? = key.get(editor)

    private val key = Key.create<NotebookCellInlayManager>(NotebookCellInlayManager::class.java.name)
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

private class UpdateInlaysTask(private val manager: NotebookCellInlayManager, lines: IntRange) : Update(Any()) {
  private val linesList = SmartList(lines)

  override fun run() {
    for (lines in linesList) {
      manager.updateImmediately(lines)
    }
  }

  override fun canEat(update: Update): Boolean {
    update as UpdateInlaysTask
    linesList.mergeAndJoinIntersections(update.linesList)
    return true
  }
}