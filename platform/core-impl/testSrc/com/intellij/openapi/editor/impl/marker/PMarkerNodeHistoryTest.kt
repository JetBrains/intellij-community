// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.application.impl.BulkArrayQueue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.ref.Reference

internal class PMarkerNodeHistoryTest {
  private val branch: PMarkerNodeBranch = PMarkerNodeBranch(null, 0)

  @Test
  fun `small histories preserve values through index and branch promotion`() {
    val history = PMarkerNodeHistory<Any>()
    val first = Any()
    val second = Any()
    val replacement = Any()
    val third = Any()
    publish(history, version(1), first)
    publish(history, version(2), second)
    publish(history, version(2), replacement)

    assertNull(field(history, "singleBranch"))
    assertNull(field(history, "branches"))
    assertSame(first, history.get(version(1)))
    assertSame(replacement, history.get(version(2)))

    publish(history, version(3), third)
    assertNotNull(field(history, "singleBranch"))
    assertNull(field(history, "branches"))
    assertSame(first, history.get(version(1)))
    assertSame(replacement, history.get(version(2)))
    assertSame(third, history.get(version(3)))

    val fork = PMarkerNodeVersion(PMarkerNodeBranch(version(2), 3), 3)
    val forkValue = Any()
    publish(history, fork, forkValue)
    assertNull(field(history, "singleBranch"))
    assertNotNull(field(history, "branches"))
    assertSame(forkValue, history.get(fork))
    assertSame(third, history.get(version(3)))
    assertSame(replacement, history.get(version(2)))
  }

  @Test
  fun `collecting the latest inline record preserves the earlier value`() {
    val history = PMarkerNodeHistory<Any>()
    val firstValue = Any()
    val first = publish(history, version(1), firstValue)
    val secondValue = Any()
    val second = publish(history, version(2), secondValue)
    collect(listOf(second))
    assertSame(firstValue, history.get(version(1)))
    assertSame(firstValue, history.get(version(2)))

    val thirdValue = Any()
    publish(history, version(3), thirdValue)
    assertSame(firstValue, history.get(version(2)))
    assertSame(thirdValue, history.get(version(3)))
    collect(listOf(first))
    assertNull(history.get(version(2)))
    assertSame(thirdValue, history.get(version(3)))
  }

  @Test
  fun `old snapshots survive head trimming and compaction`() {
    val history = PMarkerNodeHistory<Any>()
    val values = List(160) { Any() }
    val references = ArrayList<Reference<*>>()
    for (index in 0 until 96) {
      references.add(publish(history, version(index * 3L), values[index]))
    }
    collect(references.take(64))
    for (index in 96 until values.size) {
      references.add(publish(history, version(index * 3L), values[index]))
    }

    assertNull(history.get(version(191)))
    for (index in 64 until values.size) {
      assertSame(values[index], history.get(version(index * 3L)))
      assertSame(values[index], history.get(version(index * 3L + 2)))
    }

    val collected = (65..159 step 2).toSet()
    collect(collected.map { references[it] })
    assertTrue(indexedRecordCount(history) < 96)
    for (index in 64 until values.size) {
      val expected = if (index in collected) index - 1 else index
      assertSame(values[expected], history.get(version(index * 3L + 2)))
    }
  }

  @Test
  fun `collection preserves a replacement at the same version`() {
    val history = PMarkerNodeHistory<Any>()
    val originalValue = Any()
    val originals = List(64) { publish(history, version(1), originalValue) }
    val replacementValue = Any()
    val replacement = publish(history, version(1), replacementValue)
    val nextValue = Any()
    publish(history, version(2), nextValue)

    collect(originals)
    assertSame(replacementValue, history.get(version(1)))
    assertSame(nextValue, history.get(version(2)))

    collect(listOf(replacement))
    assertNull(history.get(version(1)))
    assertSame(nextValue, history.get(version(2)))
  }

  @Test
  fun `branches inherit only through their fork versions after collection`() {
    val history = PMarkerNodeHistory<Any>()
    val firstValue = Any()
    val thirdValue = Any()
    publish(history, version(1), firstValue)
    publish(history, version(3), thirdValue)
    val fork = PMarkerNodeBranch(version(1), 2)
    val forkVersion = PMarkerNodeVersion(fork, 2)
    assertSame(firstValue, history.get(forkVersion))

    val forkValue = Any()
    val forkReference = publish(history, forkVersion, forkValue)
    val nestedVersion = PMarkerNodeVersion(PMarkerNodeBranch(forkVersion, 3), 3)
    val parentValue = Any()
    publish(history, version(4), parentValue)
    val futureValue = Any()
    val futureVersion = PMarkerNodeVersion(fork, 4)
    publish(history, futureVersion, futureValue)
    assertSame(forkValue, history.get(nestedVersion))
    assertSame(futureValue, history.get(futureVersion))
    assertSame(parentValue, history.get(version(4)))

    collect(listOf(forkReference))
    assertSame(firstValue, history.get(nestedVersion))
    assertSame(firstValue, history.get(forkVersion))
    assertSame(futureValue, history.get(futureVersion))
    assertSame(thirdValue, history.get(version(3)))
    assertSame(parentValue, history.get(version(4)))
  }

  @Test
  fun `successors from one version keep independent histories`() {
    val history = PMarkerNodeHistory<Any>()
    val initial = version(0)
    val first = PMarkerNodeHistory.nextVersion(initial)
    val second = PMarkerNodeHistory.nextVersion(initial)
    val firstValue = Any()
    val secondValue = Any()
    publish(history, first, firstValue)
    publish(history, second, secondValue)

    assertSame(firstValue, history.get(PMarkerNodeHistory.nextVersion(first)))
    assertSame(secondValue, history.get(PMarkerNodeHistory.nextVersion(second)))
    assertNull(history.get(initial))
  }

  private fun version(number: Long): PMarkerNodeVersion = PMarkerNodeVersion(branch, number)

  private fun publish(history: PMarkerNodeHistory<Any>, version: PMarkerNodeVersion, value: Any): Reference<*> {
    history.put(version, value)
    return PMarkerNodeHistory::class.java.getDeclaredField("latest").apply { isAccessible = true }.get(history) as Reference<*>
  }

  private fun collect(references: List<Reference<*>>) {
    for (reference in references) {
      reference.clear()
      reference.enqueue()
    }
    PMarkerNodeHistory.Companion::class.java.getDeclaredMethod("collect", Int::class.javaPrimitiveType)
      .apply { isAccessible = true }.invoke(PMarkerNodeHistory, Int.MAX_VALUE)
  }

  private fun indexedRecordCount(history: PMarkerNodeHistory<Any>): Int {
    val singleBranch = field(history, "singleBranch")
    val branches = field(history, "branches") as Map<*, *>?
    val histories = if (singleBranch == null) branches?.values.orEmpty() else listOf(singleBranch)
    return histories.sumOf { branch ->
      val records = branch!!.javaClass.getDeclaredField("records").apply { isAccessible = true }.get(branch) as BulkArrayQueue<*>
      records.size()
    }
  }

  private fun field(history: PMarkerNodeHistory<Any>, name: String): Any? =
    PMarkerNodeHistory::class.java.getDeclaredField(name).apply { isAccessible = true }.get(history)
}
