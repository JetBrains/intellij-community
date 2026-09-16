// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer
import com.intellij.collaboration.util.SequenceComputer.ComputationOutcome
import com.intellij.collaboration.util.SequenceItem
import com.intellij.collaboration.util.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ListPartSequenceWithMutableCacheTest {

  @Test
  fun `emits all parts in order preserving the isLast flag`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1, 2), listOf(3, 4)))

    val parts = loader.toList()

    assertEquals(listOf(listOf(1, 2), listOf(3, 4)), parts.map { it.value })
    assertEquals(listOf(false, true), parts.map { it.isLast })
  }

  @Test
  fun `an empty source yields nothing`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf())

    assertEquals(emptyList<List<Int>>(), loader.loadedLists())
  }

  @Test
  fun `disposing the cache doesn't cancel the caller`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1, 2)))
    bg.cancelAndJoin()

    val outcome = loader.getComputer().computeNext()
    assertEquals(ComputationOutcome.Done, outcome) { "cancelled computer returned data" }
  }

  @Test
  fun `parts are computed once and served from cache on repeated consumption`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val source = sourceOf(listOf(1), listOf(2))
    val loader = ListPartSequenceWithMutableCache(bg, source)

    assertEquals(listOf(listOf(1), listOf(2)), loader.loadedLists())
    assertEquals(listOf(listOf(1), listOf(2)), loader.loadedLists()) // fully served from cache

    assertEquals(2, source.partsComputed) // each part is computed exactly once, not re-fetched
    assertEquals(1, source.computersCreated) // the underlying sequence is walked by a single computer
  }

  @Test
  fun `updateItem replaces the first accepted item and stops`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1, 2, 3), listOf(2, 4)))
    loader.toList() // materialize the cache

    loader.updateItem { if (it == 2) 20 else null }

    // only the first 2 (in the first part) is replaced; the 2 in the second part is left untouched
    assertEquals(listOf(listOf(1, 20, 3), listOf(2, 4)), loader.loadedLists())
  }

  @Test
  fun `updateItem can replace an item in a later part`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1, 2), listOf(3, 4)))
    loader.toList()

    loader.updateItem { if (it == 3) 30 else null }

    assertEquals(listOf(listOf(1, 2), listOf(30, 4)), loader.loadedLists())
  }

  @Test
  fun `updateItem is a no-op when the updater accepts nothing`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1, 2), listOf(3, 4)))
    loader.toList()

    loader.updateItem { null }

    assertEquals(listOf(listOf(1, 2), listOf(3, 4)), loader.loadedLists())
  }

  @Test
  fun `updateItem preserves the isLast flag of the mutated part`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1), listOf(2)))
    loader.toList()

    loader.updateItem { if (it == 2) 20 else null }

    assertEquals(listOf(false, true), loader.toList().map { it.isLast })
  }

  @Test
  fun `updateItem before anything is loaded is dropped`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1, 2)))

    loader.updateItem { 99 } // nothing has been consumed yet, so there is no cache to mutate

    assertEquals(listOf(listOf(1, 2)), loader.loadedLists())
  }

  @Test
  fun `reset discards cached mutations and reloads from the source`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val source = sourceOf(listOf(1, 2))
    val loader = ListPartSequenceWithMutableCache(bg, source)

    loader.toList()
    loader.updateItem { if (it == 1) 10 else null }
    assertEquals(listOf(listOf(10, 2)), loader.loadedLists())

    loader.reset()

    assertEquals(listOf(listOf(1, 2)), loader.loadedLists()) // the mutation is gone, values are fresh from the source
    assertEquals(2, source.computersCreated) // a brand-new computer is created after the reset
  }

  @Test
  fun `reset makes the loader observe fresh source data`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val source = MutableSource(partsOf(listOf(1, 2)))
    val loader = ListPartSequenceWithMutableCache(bg, source)

    assertEquals(listOf(listOf(1, 2)), loader.loadedLists())

    source.parts = partsOf(listOf(3, 4)) // the underlying data changes...
    assertEquals(listOf(listOf(1, 2)), loader.loadedLists()) // ...but the cache keeps serving the old snapshot

    loader.reset()

    assertEquals(listOf(listOf(3, 4)), loader.loadedLists()) // after the reset the new data is walked
  }

  @Test
  fun `reset before anything is loaded is a no-op`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ListPartSequenceWithMutableCache(bg, sourceOf(listOf(1, 2)))

    loader.reset() // nothing has been initialized yet, so there is nothing to drop

    assertEquals(listOf(listOf(1, 2)), loader.loadedLists())
  }
}

/** A source [ComputableSequence] that records how many computers it hands out and how many parts it actually computes. */
private class RecordingSource(private val parts: List<ListPart<Int>>) : ComputableSequence<ListPart<Int>> {
  var computersCreated: Int = 0
    private set
  var partsComputed: Int = 0
    private set

  override fun getComputer(): SequenceComputer<ListPart<Int>> {
    computersCreated++
    return object : SequenceComputer<ListPart<Int>> {
      private var index = 0
      override suspend fun computeNext(): ComputationOutcome<ListPart<Int>> {
        if (index >= parts.size) return ComputationOutcome.Done
        partsComputed++
        return ComputationOutcome.Item(parts[index++])
      }
    }
  }
}

/** Builds [ListPart]s from raw lists, flagging the final one as the last. */
private fun partsOf(vararg lists: List<Int>): List<ListPart<Int>> =
  lists.mapIndexed { i, list -> SequenceItem(list, isLast = i == lists.lastIndex) }

/** A source whose backing parts can change between walks; each computer snapshots the parts at creation time. */
private class MutableSource(var parts: List<ListPart<Int>>) : ComputableSequence<ListPart<Int>> {
  override fun getComputer(): SequenceComputer<ListPart<Int>> {
    val snapshot = parts
    return object : SequenceComputer<ListPart<Int>> {
      private var index = 0
      override suspend fun computeNext(): ComputationOutcome<ListPart<Int>> =
        if (index < snapshot.size) ComputationOutcome.Item(snapshot[index++]) else ComputationOutcome.Done
    }
  }
}

private fun sourceOf(vararg lists: List<Int>): RecordingSource = RecordingSource(partsOf(*lists))

private suspend fun ListPartSequenceWithMutableCache<Int>.loadedLists(): List<List<Int>> = toList().map { it.value }
