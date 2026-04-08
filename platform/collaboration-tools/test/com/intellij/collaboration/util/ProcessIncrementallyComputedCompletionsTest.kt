// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class ProcessIncrementallyComputedCompletionsTest {
  @Test
  fun `emits only newly added elements for partial success updates`() = runTest {
    val resultSet = mockk<CompletionResultSet>()
    val addedElements = mutableListOf<LookupElement>()

    every { resultSet.addAllElements(any()) } answers {
      addedElements += firstArg<List<LookupElement>>()
    }

    val state = MutableStateFlow(IncrementallyComputedValue.loading<List<Int>>())
    val processed = mutableListOf<Int>()

    val job = launch {
      state.consumeIncrementally(
        batchConsumer = { newItems ->
          processed += newItems
          resultSet.addAllElements(newItems.map { LookupElementBuilder.create(it.toString()) })
        },
        onError = { error("Unexpected error: $it") },
      )
    }

    state.value = IncrementallyComputedValue.partialSuccess(listOf(1, 2))
    state.value = IncrementallyComputedValue.partialSuccess(listOf(1, 2, 3))
    state.value = IncrementallyComputedValue.success(listOf(1, 2, 3, 4))

    job.join()

    assertEquals(listOf(1, 2, 3, 4), processed)
    assertEquals(listOf("1", "2", "3", "4"), addedElements.map { it.lookupString })
  }

  @Test
  fun `stops and reports error on failure`() = runTest {
    val resultSet = mockk<CompletionResultSet>()
    val addedElements = mutableListOf<LookupElement>()
    val errors = mutableListOf<Exception>()

    every { resultSet.addAllElements(any()) } answers {
      addedElements += firstArg<List<LookupElement>>()
    }

    val state = MutableStateFlow(IncrementallyComputedValue.loading<List<Int>>())

    val job = launch {
      state.consumeIncrementally(
        batchConsumer = { newItems ->
          resultSet.addAllElements(newItems.map { LookupElementBuilder.create(it.toString()) })
        },
        onError = { errors += it },
      )
    }

    val expected = IllegalStateException("exception")
    state.value = IncrementallyComputedValue.partialSuccess(listOf(1))
    state.value = IncrementallyComputedValue.partialFailure(listOf(1, 2), expected)

    job.join()

    assertEquals(listOf("1", "2"), addedElements.map { it.lookupString })
    assertEquals(listOf(expected), errors)
  }

  @Test
  fun `instant failure`() = runTest {
    val resultSet = mockk<CompletionResultSet>()
    val addedElements = mutableListOf<LookupElement>()
    val errors = mutableListOf<Exception>()

    every { resultSet.addAllElements(any()) } answers {
      addedElements += firstArg<List<LookupElement>>()
    }

    val state = MutableStateFlow(IncrementallyComputedValue.loading<List<Int>>())

    val job = launch {
      state.consumeIncrementally(
        batchConsumer = { newItems ->
          resultSet.addAllElements(newItems.map { LookupElementBuilder.create(it.toString()) })
        },
        onError = { errors += it },
      )
    }

    val expected = IllegalStateException("exception")
    state.value = IncrementallyComputedValue.failure(expected)

    job.join()

    assertEquals(emptyList<String>(), addedElements.map { it.lookupString })
    assertEquals(listOf(expected), errors)
  }

  @Test
  fun `no items received`() = runTest {
    val resultSet = mockk<CompletionResultSet>()
    val addedElements = mutableListOf<LookupElement>()

    every { resultSet.addAllElements(any()) } answers {
      addedElements += firstArg<List<LookupElement>>()
    }

    val state = MutableStateFlow(IncrementallyComputedValue.loading<List<Int>>())

    val job = launch {
      state.consumeIncrementally(
        batchConsumer = { newItems ->
          resultSet.addAllElements(newItems.map { LookupElementBuilder.create(it.toString()) })
        },
        onError = { error("Unexpected error: $it") },
      )
    }

    state.value = IncrementallyComputedValue.success(emptyList())

    job.join()

    assertEquals(emptyList<String>(), addedElements.map { it.lookupString })
  }

  @Test
  fun `does not call result set before any value is available`() = runTest {
    val resultSet = mockk<CompletionResultSet>(relaxed = true)
    val state = MutableStateFlow(IncrementallyComputedValue.loading<List<Int>>())

    val job = async {
      state.consumeIncrementally(
        batchConsumer = { newItems ->
          resultSet.addAllElements(newItems.map { LookupElementBuilder.create(it.toString()) })
        },
        onError = { error("Unexpected error: $it") },
      )
    }

    kotlinx.coroutines.delay(50.milliseconds)
    job.cancel()

    verify(exactly = 0) { resultSet.addAllElements(any()) }
  }
}