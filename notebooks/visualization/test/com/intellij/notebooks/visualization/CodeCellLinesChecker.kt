package com.intellij.notebooks.visualization

import com.intellij.lang.Language
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.keyFMap.KeyFMap
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCollection
import kotlin.reflect.KProperty1

class CodeCellLinesChecker(private val description: String,
                           private val intervalsComparator: Comparator<NotebookCellLines.Interval> = makeIntervalComparatorIgnoringData(),
                           private val markersComparator: Comparator<NotebookCellLinesLexer.Marker> = makeMarkerComparatorIgnoringData(),
                           private val editorGetter: () -> EditorImpl) : (CodeCellLinesChecker.() -> Unit) -> Unit {
  private var markers: MutableList<NotebookCellLinesLexer.Marker>? = null
  private var intervals: MutableList<NotebookCellLines.Interval>? = null
  private var markersStartOffset: Int = 0
  private var markersStartOrdinal: Int = 0
  private var intervalsStartLine: Int = 0
  private val expectedIntervalListenerCalls = mutableListOf<Pair<List<NotebookCellLines.Interval>, List<NotebookCellLines.Interval>>>()

  inner class MarkersSetter {
    init {
      markers = mutableListOf()
    }

    fun marker(cellType: NotebookCellLines.CellType, offset: Int, length: Int, language: Language) {
      markers!!.add(
        NotebookCellLinesLexer.Marker(ordinal = markers!!.size + markersStartOrdinal, type = cellType, offset = offset, length = length,
                                      data = makeLanguageData(language)))
    }
  }

  fun markers(startOffset: Int = 0, startOrdinal: Int = 0, handler: MarkersSetter.() -> Unit) {
    markersStartOffset = startOffset
    markersStartOrdinal = startOrdinal
    check(markers == null) { "markers{} section defined twice" }
    MarkersSetter().handler()
  }

  class IntervalsSetter(private val list: MutableList<NotebookCellLines.Interval>, private val startOrdinal: Int) {
    fun interval(cellType: NotebookCellLines.CellType,
                 lines: IntRange,
                 markers: NotebookCellLines.MarkersAtLines,
                 language: Language) {
      list += NotebookCellLines.Interval(list.size + startOrdinal, cellType, lines, markers, makeLanguageData(language))
    }

    fun interval(cellType: NotebookCellLines.CellType, lines: IntRange, language: Language) {
      val markers =
        if (cellType == NotebookCellLines.CellType.RAW && lines.first == 0)
          NotebookCellLines.MarkersAtLines.NO
        else
          NotebookCellLines.MarkersAtLines.TOP
      interval(cellType, lines, markers, language)
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
      override fun documentChanged(event: NotebookCellLinesEvent) {
        if (event.isIntervalsChanged()) {
          actualIntervalListenerCalls += event.oldIntervals to event.newIntervals
        }
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
        assertThatCollection(makeMarkersFromIntervals(editor.document, codeCellLines.intervals).filter { it.offset >= markersStartOffset })
          .describedAs("Markers: $descr")
          .usingElementComparator(markersComparator.toJavaComparatorNonNullable())
          .isEqualTo(markers)
      }
      intervals?.let { intervals ->
        assertThatCollection(codeCellLines.intervalsIterator(intervalsStartLine).asSequence().toList())
          .describedAs("Intervals: $descr")
          .usingElementComparator(intervalsComparator.toJavaComparatorNonNullable())
          .isEqualTo(intervals)
      }
    }

    // actually this should match intervalsComparator
    fun toPrettyString(interval: NotebookCellLines.Interval): String {
      fun <T> field(property: KProperty1<NotebookCellLines.Interval, T>): String =
        "${property.name} = ${property.get(interval)}"

      return listOf(
        field(NotebookCellLines.Interval::ordinal),
        field(NotebookCellLines.Interval::type),
        field(NotebookCellLines.Interval::lines),
        field(NotebookCellLines.Interval::markers),
        field(NotebookCellLines.Interval::language),
      ).joinToString(", ", prefix = "Interval(", postfix = ")")
    }

    fun List<Pair<List<NotebookCellLines.Interval>, List<NotebookCellLines.Interval>>>.prettyListeners() =
      withIndex().joinToString("\n\n") { (idx, pair) ->
        """
        Call #$idx
          Before:
        ${pair.first.joinToString { "    ${toPrettyString(it)}" }}
          After:
        ${pair.second.joinToString { "    ${toPrettyString(it)}" }}
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

  companion object {
    fun makeIntervalComparatorIgnoringData(): Comparator<NotebookCellLines.Interval> =
      compareBy<NotebookCellLines.Interval> { it.ordinal }
        .thenBy { it.type }
        .thenBy { it.lines.first }
        .thenBy { it.lines.last }
        .thenBy { it.markers }
        .thenBy { it.language.id }

    fun makeMarkerComparatorIgnoringData(): Comparator<NotebookCellLinesLexer.Marker> =
      compareBy<NotebookCellLinesLexer.Marker> { it.ordinal }
        .thenBy { it.type }
        .thenBy { it.offset }
        .thenBy { it.length }

    // workaround for kotlin type system
    private fun <T> Comparator<T>.toJavaComparatorNonNullable(): java.util.Comparator<Any?> =
      object : java.util.Comparator<Any?> {
        override fun compare(o1: Any?, o2: Any?): Int {
          return this@toJavaComparatorNonNullable.compare(o1 as T, o2 as T)
        }
      }

    private fun makeLanguageData(language: Language) =
      KeyFMap.EMPTY_MAP.plus(NotebookCellLines.INTERVAL_LANGUAGE_KEY, language)
  }
}

