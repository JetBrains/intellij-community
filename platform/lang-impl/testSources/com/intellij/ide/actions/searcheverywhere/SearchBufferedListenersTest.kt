// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.containers.stream
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.integration.junit4.JUnit4Mockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Test


class SearchBufferedListenersTest : BasePlatformTestCase() {

  private var myMockery: Mockery? = null

  //@Before
  override fun setUp() {
    super.setUp()
    myMockery = object : JUnit4Mockery() {
      init {
        setImposteriser(ClassImposteriser.INSTANCE)
      }
    }
  }

  @Test
  fun `test events order for ThrottlingListenerWrapper`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java, "mock1")
    val throttlingWrapper = ThrottlingListenerWrapper(mockListener)
    val contributor = createDumbContributor("Test")

    val elements = generateElementsBatch(contributor, "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8")
    myMockery!!.checking(object : Expectations(){
      init{
        oneOf(mockListener).searchStarted(listOf(contributor))
        oneOf(mockListener).elementsAdded(elements)
        one(mockListener).searchFinished(mapOf(Pair(contributor, true)))
      }
    })

    with(throttlingWrapper) {
      searchStarted(listOf(contributor))
      elementsAdded(elements.subList(0, 4))
      elementsAdded(elements.subList(4, 6))
      elementsAdded(elements.subList(6, 8))
      contributorFinished(contributor, true)
      flushBuffer() //todo delete after refactoring
      searchFinished(mapOf(Pair(contributor, true)))
    }

    myMockery!!.assertIsSatisfied()
  }

  @Test
  fun `test SwitchSEListener collects events to buffer with single contributor`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java, "mock1")
    val wfcWrapper = SwitchSEListener(mockListener, MixedSearchListModel())
    val contributor = createDumbContributor("Test")

    val elements = generateElementsBatch(contributor, "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8")
    myMockery!!.checking(object : Expectations(){
      init{
        oneOf(mockListener).searchStarted(listOf(contributor))
        oneOf(mockListener).elementsAdded(elements)
        one(mockListener).searchFinished(mapOf(Pair(contributor, true)))
      }
    })

    with(wfcWrapper) {
      searchStarted(listOf(contributor))
      elementsAdded(elements.subList(0, 4))
      elementsAdded(elements.subList(4, 6))
      elementsAdded(elements.subList(6, 8))
      contributorFinished(contributor, true)
      flushBuffer() //todo delete after refactoring
      searchFinished(mapOf(Pair(contributor, true)))
    }

    myMockery!!.assertIsSatisfied()
  }

  fun `test SwitchSEListener collects events to buffer with few contributors`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val wfcWrapper = SwitchSEListener(mockListener, MixedSearchListModel())
    val contributor1 = createDumbContributor("Test1")
    val contributor2 = createDumbContributor("Test2")

    val elements = generateElementsBatch(
      "e1" to contributor1,
      "e2" to contributor2,
      "e3" to contributor2,
      "e4" to contributor1,
      "e5" to contributor1,
      "e6" to contributor2
    )

    myMockery!!.checking(object : Expectations(){
      init{
        oneOf(mockListener).searchStarted(listOf(contributor1, contributor2))
        oneOf(mockListener).elementsAdded(elements)
        one(mockListener).searchFinished(mapOf(Pair(contributor1, true), Pair(contributor2, false)))
      }
    })

    with(wfcWrapper) {
      searchStarted(listOf(contributor1, contributor2))
      elementsAdded(elements.subList(0, 3))
      elementsAdded(elements.subList(3, 6))
      contributorFinished(contributor1, true)
      contributorFinished(contributor2, false)
      searchFinished(mapOf(Pair(contributor1, true), Pair(contributor2, false)))
    }

    myMockery!!.assertIsSatisfied()
  }

  fun `test previous search items are cleared (ThrottlingListenerWrapper)`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val throttlingWrapper = ThrottlingListenerWrapper(mockListener)
    doTestClearPreviousResults(mockListener, throttlingWrapper)
  }

  fun `test previous search items are cleared (SwitchSEListener)`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val wfcWrapper = SwitchSEListener(mockListener, MixedSearchListModel())
    doTestClearPreviousResults(mockListener, wfcWrapper)
  }

  private fun doTestClearPreviousResults(mockListener: SearchListener, wrapper: BufferingListenerWrapper) {
    val contributor = createDumbContributor("Test1")
    val elements = generateElementsBatch(contributor, "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8")

    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted(listOf(contributor))
        oneOf(mockListener).searchStarted(listOf(contributor))
        oneOf(mockListener).elementsAdded(elements.subList(4, 8))
        oneOf(mockListener).searchFinished(mapOf(Pair(contributor, true)))
      }
    })

    with(wrapper) {
      searchStarted(listOf(contributor))
      elementsAdded(elements.subList(0, 4))
      clearBuffer() //todo delete after refactoring
      searchStarted(listOf(contributor))
      elementsAdded(elements.subList(4, 8))
      contributorFinished(contributor, true)
      flushBuffer() //todo delete after refactoring
      searchFinished(mapOf(Pair(contributor, true)))
    }
  }

  private fun generateElementsBatch(vararg pairs: Pair<Any, SearchEverywhereContributor<*>>): MutableList<SearchEverywhereFoundElementInfo> =
    pairs.stream().map { SearchEverywhereFoundElementInfo(it.first, 0, it.second) }.toList()

  private fun generateElementsBatch(contributor: SearchEverywhereContributor<*>, vararg elements: Any): MutableList<SearchEverywhereFoundElementInfo> =
    elements.stream().map { SearchEverywhereFoundElementInfo(it, 0, contributor) }.toList()

}