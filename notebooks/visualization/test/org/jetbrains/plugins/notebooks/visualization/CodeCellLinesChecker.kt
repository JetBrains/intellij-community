package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.impl.EditorImpl
import org.assertj.core.api.Assertions.assertThat

class CodeCellLinesChecker(private val description: String,
                           private val editorGetter: () -> EditorImpl) : (CodeCellLinesChecker.() -> Unit) -> Unit {
  private var markers: MutableList<NotebookCellLines.Marker>? = null
  private var intervals: MutableList<NotebookCellLines.Interval>? = null
  private var markersStartOffset: Int = 0
  private var markersStartOrdinal: Int = 0
  private var intervalsStartLine: Int = 0
  private val expectedIntervalListenerCalls = mutableListOf<Pair<List<NotebookCellLines.Interval>, List<NotebookCellLines.Interval>>>()

  inner class MarkersSetter {
    init {
      markers = mutableListOf()
    }

    fun marker(cellType: NotebookCellLines.CellType, offset: Int, length: Int) {
      markers!!.add(
        NotebookCellLines.Marker(ordinal = markers!!.size + markersStartOrdinal, type = cellType, offset = offset, length = length))
    }
  }

  fun markers(startOffset: Int = 0, startOrdinal: Int = 0, handler: MarkersSetter.() -> Unit) {
    markersStartOffset = startOffset
    markersStartOrdinal = startOrdinal
    check(markers == null) { "markers{} section defined twice" }
    MarkersSetter().handler()
  }

  class IntervalsSetter(private val list: MutableList<NotebookCellLines.Interval>, private val startOrdinal: Int) {
    fun interval(cellType: NotebookCellLines.CellType, lines: IntRange, markers: NotebookCellLines.MarkersAtLines) {
      list += NotebookCellLines.Interval(list.size + startOrdinal, cellType, lines, markers)
    }
  }

  fun intervals(startLine: Int = 0, startOrdinal: Int = 0, handler: IntervalsSetter.() -> Unit) {
    intervalsStartLine = startLine
    check(intervals == null) { "intervals{} section defined twice" }
    intervals = mutableListOf()
    IntervalsSetter(intervals!!, startOrdinal).handler()
  }

  class IntervalListenerCalls(
    private val startOrdinal: Int,
    private val before: MutableList<NotebookCellLines.Interval>,
    private val after: MutableList<NotebookCellLines.Interval>
  ) {
    fun before(handler: IntervalsSetter.() -> Unit) {
      IntervalsSetter(before, startOrdinal).handler()
    }

    fun after(handler: IntervalsSetter.() -> Unit) {
      IntervalsSetter(after, startOrdinal).handler()
    }
  }

  fun intervalListenerCall(startOrdinal: Int, handler: IntervalListenerCalls.() -> Unit) {
    val before = mutableListOf<NotebookCellLines.Interval>()
    val after = mutableListOf<NotebookCellLines.Interval>()
    IntervalListenerCalls(startOrdinal, before, after).handler()
    expectedIntervalListenerCalls += before to after
  }

  override fun invoke(handler: CodeCellLinesChecker.() -> Unit) {
    val actualIntervalListenerCalls = mutableListOf<Pair<List<NotebookCellLines.Interval>, List<NotebookCellLines.Interval>>>()
    val intervalListener = object : NotebookCellLines.IntervalListener {
      override fun segmentChanged(oldIntervals: List<NotebookCellLines.Interval>,
                                  newIntervals: List<NotebookCellLines.Interval>,
                                  eventAffectedIntervals: List<NotebookCellLines.Interval>) {
        if (oldIntervals.isEmpty() && newIntervals.isEmpty()) return
        actualIntervalListenerCalls += oldIntervals to newIntervals
      }
    }
    val editor = editorGetter()
    val codeCellLines = NotebookCellLines.get(editor)
    codeCellLines.intervalListeners.addListener(intervalListener)
    val prettyDocumentTextBefore = editorGetter().prettyText

    try {
      handler()
    }
    catch (err: Throwable) {
      val message =
        try {
          "$err: ${err.message}\nDocument is: ${editorGetter().prettyText}"
        }
        catch (ignored: Throwable) {
          throw err
        }
      throw IllegalStateException(message, err)
    }
    finally {
      codeCellLines.intervalListeners.removeListener(intervalListener)
    }

    val prettyDocumentTextAfter = editorGetter().prettyText

    for (attempt in 0..1) {
      val descr = """
        |||$description${if (attempt > 0) " (repeat to check idempotence)" else ""}
        |||Document before: $prettyDocumentTextBefore
        |||Document after: $prettyDocumentTextAfter
        """.trimMargin("|||")

      markers.let { markers ->
        assertThat(makeMarkersFromIntervals(editor.document, codeCellLines.intervals).filter { it.offset >= markersStartOffset })
          .describedAs("Markers: $descr")
          .isEqualTo(markers)
      }
      intervals?.let { intervals ->
        assertThat(codeCellLines.intervalsIterator(intervalsStartLine).asSequence().toList())
          .describedAs("Intervals: $descr")
          .isEqualTo(intervals)
      }
    }

    fun List<Pair<List<NotebookCellLines.Interval>, List<NotebookCellLines.Interval>>>.prettyListeners() =
      withIndex().joinToString("\n\n") { (idx, pair) ->
        """
        Call #$idx
          Before:
        ${pair.first.joinToString { "    $it" }}
          After:
        ${pair.second.joinToString { "    $it" }}
        """.trimIndent()
      }

    assertThat(actualIntervalListenerCalls.prettyListeners())
      .describedAs("""
        |||Calls of IntervalListener: $description
        |||Document before: $prettyDocumentTextBefore
        |||Document after: $prettyDocumentTextAfter
        """.trimMargin("|||"))
      .isEqualTo(expectedIntervalListenerCalls.prettyListeners())
  }
}