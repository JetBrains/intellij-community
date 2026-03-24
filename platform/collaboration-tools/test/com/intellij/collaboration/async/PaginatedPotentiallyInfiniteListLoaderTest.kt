// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class PaginatedPotentiallyInfiniteListLoaderTest {
  private data class DummyData(
    val key: Int,
  )

  private data class DummyPageInfo(
    val offset: Int,
    val hasNext: Boolean,
  ) : PaginatedPotentiallyInfiniteListLoader.PageInfo<DummyPageInfo> {
    override fun createNextPageInfo(): DummyPageInfo? =
      if (hasNext) copy(offset = offset + PAGE_SIZE) else null
  }

  private data class DummyPage(
    val pageInfo: DummyPageInfo?,
    val data: List<DummyData>?,
  )

  private fun interface PageLoader {
    fun computePage(offset: Int): DummyPage?
  }

  companion object {
    private const val PAGE_SIZE = 20
    private val ALL_TEST_DATA: List<DummyData> = (0 until 100).map { DummyData(it) }

    private fun pageLoaderMock(sizeLimiter: () -> Int = { ALL_TEST_DATA.size }): PageLoader =
      mockk<PageLoader> {
        coEvery { computePage(any()) }.coAnswers {
          val offset = firstArg() as Int
          val virtualSize = minOf(ALL_TEST_DATA.size, sizeLimiter())
          val endIndex = minOf(virtualSize, offset + PAGE_SIZE)

          if (offset >= virtualSize) return@coAnswers null

          val data = ALL_TEST_DATA.subList(offset, endIndex)
          val hasNext = endIndex < virtualSize
          DummyPage(DummyPageInfo(offset = offset, hasNext = hasNext), data)
        }
      }
  }

  private class TestLoader(
    shouldTryToLoadAll: Boolean,
    private val pageLoader: PageLoader,
  ) : PaginatedPotentiallyInfiniteListLoader<DummyPageInfo, Int, DummyData>(
    initialPageInfo = DummyPageInfo(offset = 0, hasNext = true),
    extractKey = { it.key },
    shouldTryToLoadAll = shouldTryToLoadAll
  ) {
    override suspend fun performRequestAndProcess(
      pageInfo: DummyPageInfo,
      createPage: (pageInfo: DummyPageInfo?, results: List<DummyData>?) -> Page<DummyPageInfo, DummyData>?,
    ): Page<DummyPageInfo, DummyData>? {
      val response = pageLoader.computePage(pageInfo.offset) ?: return null
      return createPage(response.pageInfo, response.data)
    }
  }

  @Test
  fun `no pages are loaded initially`() = runTest {
    val pageLookupMock = pageLoaderMock(ALL_TEST_DATA::size)
    val listLoader = TestLoader(shouldTryToLoadAll = false, pageLoader = pageLookupMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()
    }

    coVerify(exactly = 0) { pageLookupMock.computePage(any()) }
  }

  @Test
  fun `one page is loaded initially with starting reload`() = runTest {
    val pageLoaderMock = pageLoaderMock()
    val listLoader = TestLoader(shouldTryToLoadAll = false, pageLoader = pageLoaderMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      listLoader.reload()

      assertThat(awaitItem().list).isNull()
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(PAGE_SIZE))
      expectNoEvents()
    }

    coVerifySequence {
      pageLoaderMock.computePage(eq(0))
    }
  }

  @Test
  fun `all pages are loaded initially with starting reload`() = runTest {
    val pageLoaderMock = pageLoaderMock()
    val listLoader = TestLoader(shouldTryToLoadAll = true, pageLoader = pageLoaderMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      listLoader.reload()

      assertThat(awaitItem().list).isNull()
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(PAGE_SIZE))
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(PAGE_SIZE * 2))
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(PAGE_SIZE * 3))
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA.take(PAGE_SIZE * 4))
      assertThat(awaitItem().list).containsExactlyElementsOf(ALL_TEST_DATA)
      expectNoEvents()
    }

    coVerifySequence {
      pageLoaderMock.computePage(eq(0))
      pageLoaderMock.computePage(eq(PAGE_SIZE))
      pageLoaderMock.computePage(eq(PAGE_SIZE * 2))
      pageLoaderMock.computePage(eq(PAGE_SIZE * 3))
      pageLoaderMock.computePage(eq(PAGE_SIZE * 4))
    }
  }

  @Test
  fun `only previously loaded pages are re-fetched upon refresh`() = runTest {
    val pageLoaderMock = pageLoaderMock()
    val listLoader = TestLoader(shouldTryToLoadAll = false, pageLoader = pageLoaderMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()

      listLoader.loadMore()

      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(PAGE_SIZE))
      expectNoEvents()

      listLoader.refresh()
      expectNoEvents()
    }

    coVerifySequence {
      // loadMore
      pageLoaderMock.computePage(eq(0))

      // refresh
      pageLoaderMock.computePage(eq(0))
    }
  }

  @Test
  fun `loadMore doesnt update state when no new data is available`() = runTest {
    val pageLoaderMock = pageLoaderMock()
    val listLoader = TestLoader(shouldTryToLoadAll = true, pageLoader = pageLoaderMock)

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
      pageLoaderMock.computePage(eq(0))
      pageLoaderMock.computePage(eq(PAGE_SIZE))
      pageLoaderMock.computePage(eq(PAGE_SIZE * 2))
      pageLoaderMock.computePage(eq(PAGE_SIZE * 3))
      pageLoaderMock.computePage(eq(PAGE_SIZE * 4))

      // loadMore - no call because hasNext is false
    }
  }

  @Test
  fun `loadMore does update state when new data is available`() = runTest {
    val pageLoaderMock = pageLoaderMock()
    val listLoader = TestLoader(shouldTryToLoadAll = false, pageLoader = pageLoaderMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()

      listLoader.loadMore()
      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(PAGE_SIZE))
      expectNoEvents()

      listLoader.loadMore()
      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(PAGE_SIZE * 2))
      expectNoEvents()
    }

    coVerifySequence {
      // loadAll
      pageLoaderMock.computePage(eq(0))

      // loadMore
      pageLoaderMock.computePage(eq(PAGE_SIZE))
    }
  }

  @Test
  fun `all pages including new ones are loaded during refresh when full loading is requested`() = runTest {
    var dynamicSize = PAGE_SIZE

    val pageLoaderMock = pageLoaderMock(sizeLimiter = { dynamicSize })
    val listLoader = TestLoader(shouldTryToLoadAll = true, pageLoader = pageLoaderMock)

    listLoader.stateFlow.test(timeout = 1.seconds) {
      assertThat(awaitItem().list).isNull()
      expectNoEvents()

      listLoader.loadAll()
      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(PAGE_SIZE))
      expectNoEvents()

      dynamicSize = PAGE_SIZE * 3

      listLoader.refresh()
      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(PAGE_SIZE * 2))
      assertThat(awaitItem().list).isEqualTo(ALL_TEST_DATA.take(PAGE_SIZE * 3))
      expectNoEvents()
    }

    coVerifySequence {
      // loadAll
      pageLoaderMock.computePage(eq(0))

      // refresh
      pageLoaderMock.computePage(eq(0))

      // loadAll after refresh
      pageLoaderMock.computePage(eq(PAGE_SIZE))
      pageLoaderMock.computePage(eq(PAGE_SIZE * 2))
    }
  }

  // TODO: Tests for (1) update function, (2) throwing an error inside fetch, (3) tracking reload/refresh flows maybe
  // For more info, run and check coverage inside com.intellij.collaboration.async
}
