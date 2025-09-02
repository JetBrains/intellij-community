// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class GraphQLListLoaderTest {
  private data class DummyData(
    val key: Int,
  )

  private class DummyDataConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<DummyData>,
  ) : GraphQLConnectionDTO<DummyData>(pageInfo, nodes)

  companion object {
    private val ALL_TEST_DATA: List<DummyData> = (0 until 100).map { DummyData(it) }

    private fun blockingPageLookup(pageSize: Int, sizeLimiter: () -> Int = { ALL_TEST_DATA.size }): suspend (cursor: String?) -> DummyDataConnection? =
      suspend@{ cursor ->
        val startIndex = cursor?.toInt() ?: 0
        val virtualSize = minOf(ALL_TEST_DATA.size, sizeLimiter())
        val endIndex = minOf(virtualSize, startIndex + pageSize)

        if (startIndex >= virtualSize) return@suspend null

        val data = ALL_TEST_DATA.subList(startIndex, endIndex)
        DummyDataConnection(GraphQLCursorPageInfoDTO(
          startCursor = startIndex.toString(), endCursor = endIndex.toString(),
          hasPreviousPage = startIndex > 0, hasNextPage = endIndex < virtualSize
        ), data)
      }
  }

  // Stub for which method invocations are tracked by Mockito
  private interface MockingPageLookup : suspend (String?) -> DummyDataConnection? {
    companion object {
      fun create(lookup: suspend (String?) -> DummyDataConnection?): MockingPageLookup = mockk<MockingPageLookup>().apply {
        coEvery { this@apply.invoke(any()) }.coAnswers { lookup(firstArg()) }
      }
    }
  }

  @Test
  fun `no pages are loaded initially`() = runTest {
    val pageLookupMock = MockingPageLookup.create(lookup = blockingPageLookup(pageSize = 10))
    val listLoader = GraphQLListLoader.startIn<Int, DummyData>(backgroundScope, extractKey = { it.key }, performRequest = pageLookupMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()
    }

    coVerify(exactly = 0) { pageLookupMock.invoke(any()) }
  }

  @Test
  fun `one page is loaded initially with starting reload`() = runTest {
    val pageLookupMock = MockingPageLookup.create(lookup = blockingPageLookup(pageSize = 20))
    val listLoader = GraphQLListLoader.startIn<Int, DummyData>(backgroundScope, extractKey = { it.key }, performRequest = pageLookupMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      listLoader.reload()

      assertThat(awaitItem().list).isNull()
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(20))
      expectNoEvents()
    }

    coVerifySequence {
      pageLookupMock.invoke(null)
    }
  }

  @Test
  fun `all pages are loaded initially with starting reload`() = runTest {
    val pageLookupMock = MockingPageLookup.create(lookup = blockingPageLookup(pageSize = 40))
    val listLoader = GraphQLListLoader.startIn<Int, DummyData>(backgroundScope, extractKey = { it.key }, shouldTryToLoadAll = true, performRequest = pageLookupMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      listLoader.reload()

      assertThat(awaitItem().list).isNull()
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(40))
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(80))
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA)
      expectNoEvents()
    }

    coVerifySequence {
      pageLookupMock.invoke(null)
      pageLookupMock.invoke(eq("40"))
      pageLookupMock.invoke(eq("80"))
      pageLookupMock.invoke(eq("100")) // impl intentionally ignores hasNext in favor of just trying to fetch
    }
  }

  @Test
  fun `only previously loaded pages are re-fetched upon refresh`() = runTest {
    val pageLookupMock = MockingPageLookup.create(lookup = blockingPageLookup(pageSize = 40))
    val listLoader = GraphQLListLoader.startIn<Int, DummyData>(backgroundScope, extractKey = { it.key }, performRequest = pageLookupMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()

      listLoader.loadMore()

      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(40))
      expectNoEvents()

      listLoader.refresh()
      expectNoEvents()
    }

    coVerifySequence {
      // loadMore
      pageLookupMock.invoke(null)

      // refresh
      pageLookupMock.invoke(null)
    }
  }

  @Test
  fun `loadMore doesnt update state when no new data is available`() = runTest {
    val pageLookupMock = MockingPageLookup.create(lookup = blockingPageLookup(pageSize = 40))
    val listLoader = GraphQLListLoader.startIn<Int, DummyData>(backgroundScope, extractKey = { it.key }, shouldTryToLoadAll = true, performRequest = pageLookupMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()

      listLoader.reload()
      while (awaitItem().list != ALL_TEST_DATA) {
      }
      expectNoEvents()

      listLoader.loadMore()
      expectNoEvents()
    }

    coVerifySequence {
      // reload
      pageLookupMock.invoke(null)
      pageLookupMock.invoke(eq("40"))
      pageLookupMock.invoke(eq("80"))
      pageLookupMock.invoke(eq("100"))

      // loadMore
      pageLookupMock.invoke(eq("100"))
    }
  }

  @Test
  fun `loadMore does update state when new data is available`() = runTest {
    var dynamicSize = 20

    val pageLookupMock = MockingPageLookup.create(lookup = blockingPageLookup(pageSize = 40, sizeLimiter = { dynamicSize }))
    val listLoader = GraphQLListLoader.startIn<Int, DummyData>(backgroundScope, extractKey = { it.key }, shouldTryToLoadAll = true, performRequest = pageLookupMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()

      listLoader.loadAll()
      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(20))
      expectNoEvents()

      dynamicSize = 60

      listLoader.loadMore()
      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(60))
    }

    coVerifySequence {
      // loadAll
      pageLookupMock.invoke(null)
      pageLookupMock.invoke(eq("20"))

      // loadMore
      pageLookupMock.invoke(eq("20"))
    }
  }

  // TODO: Tests for (1) update function, (2) throwing an error inside fetch, (3) tracking reload/refresh flows maybe
  // For more info, run and check coverage inside com.intellij.collaboration.async
}