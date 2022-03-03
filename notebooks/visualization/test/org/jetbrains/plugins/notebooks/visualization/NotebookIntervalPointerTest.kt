package org.jetbrains.plugins.notebooks.visualization

import com.intellij.util.EventDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.Descriptable
import org.assertj.core.description.Description
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines.Interval
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


@RunWith(JUnit4::class)
class NotebookIntervalPointerTest {
  private val exampleIntervals = listOf(
    makeIntervals(),
    makeIntervals(0..1),
    makeIntervals(0..1, 2..4),
    makeIntervals(0..1, 2..4, 5..8),
  )

  @Test
  fun testInitialization() {
    for (intervals in exampleIntervals) {
      val env = TestEnv(intervals)
      env.shouldBeValid(intervals)
      env.shouldBeInvalid(makeInterval(10, 10..11))
    }
  }

  @Test
  fun testAddAllIntervals() {
    for (intervals in exampleIntervals) {
      val env = TestEnv(listOf())
      env.shouldBeInvalid(intervals)
      env.changeSegment(listOf(), intervals, intervals)
      env.shouldBeValid(intervals)
    }
  }

  @Test
  fun testRemoveAllIntervals() {
    for (intervals in exampleIntervals) {
      val env = TestEnv(intervals)
      env.shouldBeValid(intervals)
      env.changeSegment(intervals, listOf(), listOf())
      env.shouldBeInvalid(intervals)
    }
  }

  @Test
  fun testChangeElements() {
    val initialIntervals = makeIntervals(0..1, 2..4, 5..8, 9..13)

    val optionsToRemove = listOf(
      listOf(),
      initialIntervals.subList(1, 2),
      initialIntervals.subList(1, 3),
      initialIntervals.subList(1, 4)
    )

    val optionsToAdd = listOf(
      listOf(),
      makeIntervals(2..10).map { it.copy(ordinal = it.ordinal + 1, type = NotebookCellLines.CellType.CODE) },
      makeIntervals(2..10, 11..20).map { it.copy(ordinal = it.ordinal + 1, type = NotebookCellLines.CellType.CODE) }
    )

    for (toRemove in optionsToRemove) {
      for (toAdd in optionsToAdd) {
        val start = initialIntervals.subList(0, 1)
        val end = initialIntervals.subList(1 + toRemove.size, 4)

        val finalIntervals = fixOrdinalsAndOffsets(start + toAdd + end)

        val env = TestEnv(initialIntervals)

        val pointersToUnchangedIntervals = (start + end).map { env.pointersFactory.create(it) }
        val pointersToRemovedIntervals = toRemove.map { env.pointersFactory.create(it) }

        pointersToUnchangedIntervals.forEach { assertThat(it.get()).isNotNull() }
        pointersToRemovedIntervals.forEach { assertThat(it.get()).isNotNull }

        env.changeSegment(toRemove, toAdd, finalIntervals)

        pointersToUnchangedIntervals.forEach { pointer -> assertThat(pointer.get()).isNotNull() }
        pointersToRemovedIntervals.forEach { pointer -> assertThat(pointer.get()).isNull() }

        env.shouldBeValid(finalIntervals)
        env.shouldBeInvalid(toRemove)
      }
    }
  }

  @Test
  fun testReuseInterval() {
    val initialIntervals = makeIntervals(0..1, 2..19, 20..199)
    for ((i, selected) in initialIntervals.withIndex()) {
      val dsize = 1
      val changed = selected.copy(lines = selected.lines.first..selected.lines.last + dsize)
      val allIntervals = fixOrdinalsAndOffsets(initialIntervals.subList(0, i) + listOf(changed) + initialIntervals.subList(i + 1, initialIntervals.size))

      val env = TestEnv(initialIntervals)

      val pointers = initialIntervals.map { env.pointersFactory.create(it) }
      pointers.zip(initialIntervals).forEach { (pointer, interval) -> assertThat(pointer.get()).isEqualTo(interval) }

      env.changeSegment(listOf(selected), listOf(changed), allIntervals)

      pointers.zip(allIntervals).forEach { (pointer, interval) -> assertThat(pointer.get()).isEqualTo(interval)}
    }
  }

  private fun fixOrdinalsAndOffsets(intervals: List<Interval>): List<Interval> {
    val result = mutableListOf<Interval>()

    for ((index, interval) in intervals.withIndex()) {
      val expectedOffset = result.lastOrNull()?.let { it.lines.last + 1 } ?: 0

      val correctInterval =
        if (interval.lines.first == expectedOffset && interval.ordinal == index)
          interval
        else
          interval.copy(ordinal = index, lines = expectedOffset..(expectedOffset + interval.lines.last - interval.lines.first))

      result.add(correctInterval)
    }

    return result
  }

  private fun makeInterval(ordinal: Int, lines: IntRange) =
    Interval(ordinal, NotebookCellLines.CellType.RAW, lines, NotebookCellLines.MarkersAtLines.NO)

  private fun makeIntervals(vararg lines: IntRange): List<Interval> =
    lines.withIndex().map { (index, lines) -> makeInterval(index, lines) }
}


private class TestEnv(intervals: List<Interval>) {
  val notebookCellLines = MockNotebookCellLines(intervals = mutableListOf(*intervals.toTypedArray()))
  val pointersFactory = NotebookIntervalPointerFactoryImpl(notebookCellLines)

  fun changeSegment(old: List<Interval>, new: List<Interval>, allIntervals: List<Interval>) {
    old.firstOrNull()?.let { firstOld ->
      new.firstOrNull()?.let { firstNew ->
        assertThat(firstOld.ordinal).isEqualTo(firstNew.ordinal)
      }
    }

    notebookCellLines.intervals.clear()
    notebookCellLines.intervals.addAll(allIntervals)
    notebookCellLines.intervalListeners.multicaster.segmentChanged(old, new)
  }

  fun shouldBeValid(interval: Interval) {
    assertThat(pointersFactory.create(interval).get())
      .describedAs { "pointer for ${interval} should be valid, but current pointers = ${describe(notebookCellLines.intervals)}" }
      .isEqualTo(interval)
  }

  fun shouldBeInvalid(interval: Interval) {
    val error = catchThrowable {
      pointersFactory.create(interval).get()
    }
    assertThat(error)
      .describedAs { "pointer for ${interval} should be null, but current pointers = ${describe(notebookCellLines.intervals)}" }
      .isNotNull()
  }

  fun shouldBeValid(intervals: List<Interval>): Unit = intervals.forEach { shouldBeValid(it) }
  fun shouldBeInvalid(intervals: List<Interval>): Unit = intervals.forEach { shouldBeInvalid(it) }
}


private class MockNotebookCellLines(override val intervals: MutableList<Interval> = mutableListOf()) : NotebookCellLines {
  init {
    checkIntervals(intervals)
  }

  override val intervalListeners = EventDispatcher.create(NotebookCellLines.IntervalListener::class.java)

  override fun intervalsIterator(startLine: Int): ListIterator<Interval> = TODO("stub")

  override val modificationStamp: Long
    get() = TODO("stub")
}


private fun describe(intervals: List<Interval>): String =
  intervals.joinToString(separator = ",", prefix = "[", postfix = "]")

private fun checkIntervals(intervals: List<Interval>) {
  intervals.zipWithNext().forEach { (prev, next) ->
    assertThat(prev.lines.last + 1).describedAs { "wrong intervals: ${describe(intervals)}" }.isEqualTo(next.lines.first)
  }

  intervals.withIndex().forEach { (index, interval) ->
    assertThat(interval.ordinal).describedAs { "wrong interval ordinal: ${describe(intervals)}" }.isEqualTo(index)
  }
}

fun <Self> Descriptable<Self>.describedAs(lazyMsg: () -> String): Self =
  describedAs(object : Description() {
    override fun value(): String = lazyMsg()
  })
