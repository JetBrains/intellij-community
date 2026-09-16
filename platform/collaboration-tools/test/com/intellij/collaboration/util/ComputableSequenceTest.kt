// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.util.SequenceComputer.ComputationOutcome
import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ComputableSequenceTest {

  @Test
  fun `emits every computed item in order`() = timeoutRunBlocking {
    assertEquals(listOf(1, 2, 3), sequenceOf(1, 2, 3).asComputedSequence().toList())
  }

  @Test
  fun `an empty sequence yields nothing`() = timeoutRunBlocking {
    val empty = ComputableSequence {
      object : SequenceComputer<Int> {
        override suspend fun computeNext(): ComputationOutcome<Int> = ComputationOutcome.Done
      }
    }
    assertEquals(emptyList<Int>(), empty.toList())
  }

  @Test
  fun `null values are emitted as items, not treated as the end`() = timeoutRunBlocking {
    val values = listOf(1, null, 3)
    assertEquals(values, values.asSequence().asComputedSequence().toList())
  }

  @Test
  fun `getComputer restarts the sequence from the beginning`() = timeoutRunBlocking {
    val seq = sequenceOf(1, 2).asComputedSequence()
    assertEquals(listOf(1, 2), seq.toList())
    assertEquals(listOf(1, 2), seq.toList())
  }

  @Test
  fun `computeNext yields each item and then reports Done`() = timeoutRunBlocking {
    val computer = sequenceOf(1, 2).asComputedSequence().getComputer()
    assertEquals(ComputationOutcome.Item(1), computer.computeNext())
    assertEquals(ComputationOutcome.Item(2), computer.computeNext())
    assertEquals(ComputationOutcome.Done, computer.computeNext())
  }

  @Test
  fun `map transforms every item`() = timeoutRunBlocking {
    assertEquals(listOf(10, 20), sequenceOf(1, 2).asComputedSequence().map { it * 10 }.toList())
  }

  @Test
  fun `computeNext is not called again once it reports Done`() = timeoutRunBlocking {
    var computeCalls = 0
    val seq = ComputableSequence {
      object : SequenceComputer<Int> {
        override suspend fun computeNext(): ComputationOutcome<Int> {
          computeCalls++
          return ComputationOutcome.Done
        }
      }
    }
    assertEquals(emptyList<Int>(), seq.toList())
    assertEquals(1, computeCalls)
  }

  @Test
  fun `byPointer walks the pointer until it is null, flagging the last item`() = timeoutRunBlocking {
    val seq = ComputableSequence.byPointer(1) { n ->
      "item$n" to (if (n < 3) n + 1 else null)
    }
    val items = seq.toList()
    assertEquals(listOf("item1", "item2", "item3"), items.map { it.value })
    assertEquals(listOf(false, false, true), items.map { it.isLast })
  }

  @Test
  fun `byPointer yields a single last item when the initial pointer is terminal`() = timeoutRunBlocking {
    val items = ComputableSequence.byPointer(0) { "only" to null }.toList()
    assertEquals(listOf(SequenceItem("only", isLast = true)), items)
  }

  @Test
  fun `SequenceComputer byPointer reports each item and then Done`() = timeoutRunBlocking {
    val computer = SequenceComputer.byPointer(1) { n -> "v$n" to (if (n < 2) n + 1 else null) }
    assertEquals(ComputationOutcome.Item(SequenceItem("v1", isLast = false)), computer.computeNext())
    assertEquals(ComputationOutcome.Item(SequenceItem("v2", isLast = true)), computer.computeNext())
    assertEquals(ComputationOutcome.Done, computer.computeNext())
  }

  @Test
  fun `byPointer restarts from the initial pointer on each getComputer`() = timeoutRunBlocking {
    val seq = ComputableSequence.byPointer(1) { n -> n to (if (n < 3) n + 1 else null) }
    assertEquals(listOf(1, 2, 3), seq.toList().map { it.value })
    assertEquals(listOf(1, 2, 3), seq.toList().map { it.value })
  }
}
