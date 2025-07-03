// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.integration.junit4.JUnit4Mockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Test
import java.awt.Toolkit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities


@Suppress("PrivatePropertyName")
class SearchBufferedListenersTest : BasePlatformTestCase() {

  private var myMockery: Mockery? = null

  private val contributor1 = createDumbContributor("Test1", essential = true)
  private val contributor2 = createDumbContributor("Test2", essential = true)
  private val contributor3 = createDumbContributor("Test3(slow)")

  private val c1_e1 = SearchEverywhereFoundElementInfoTest("c1_e1", contributor1)
  private val c1_e2 = SearchEverywhereFoundElementInfoTest("c1_e2", contributor1)
  private val c1_e3 = SearchEverywhereFoundElementInfoTest("c1_e3", contributor1)
  private val c1_e4 = SearchEverywhereFoundElementInfoTest("c1_e4", contributor1)
  private val c1_e5 = SearchEverywhereFoundElementInfoTest("c1_e5", contributor1)
  private val c1_e6 = SearchEverywhereFoundElementInfoTest("c1_e6", contributor1)
  private val c1_e7 = SearchEverywhereFoundElementInfoTest("c1_e7", contributor1)
  private val c1_e8 = SearchEverywhereFoundElementInfoTest("c1_e8", contributor1)
  private val c2_e1 = SearchEverywhereFoundElementInfoTest("c2_e1", contributor2)
  private val c2_e2 = SearchEverywhereFoundElementInfoTest("c2_e2", contributor2)
  private val c2_e3 = SearchEverywhereFoundElementInfoTest("c2_e3", contributor2)
  private val c2_e4 = SearchEverywhereFoundElementInfoTest("c2_e4", contributor2)
  private val c2_e5 = SearchEverywhereFoundElementInfoTest("c2_e5", contributor2)
  private val c2_e6 = SearchEverywhereFoundElementInfoTest("c2_e6", contributor2)
  private val c2_e7 = SearchEverywhereFoundElementInfoTest("c2_e7", contributor2)
  private val c2_e8 = SearchEverywhereFoundElementInfoTest("c2_e8", contributor2)
  private val c3_e1 = SearchEverywhereFoundElementInfoTest("c3_e1", contributor3)
  private val c3_e2 = SearchEverywhereFoundElementInfoTest("c3_e2", contributor3)
  private val c3_e3 = SearchEverywhereFoundElementInfoTest("c3_e3", contributor3)
  private val c3_e4 = SearchEverywhereFoundElementInfoTest("c3_e4", contributor3)
  private val c3_e5 = SearchEverywhereFoundElementInfoTest("c3_e5", contributor3)
  private val c3_e6 = SearchEverywhereFoundElementInfoTest("c3_e6", contributor3)
  private val c3_e7 = SearchEverywhereFoundElementInfoTest("c3_e7", contributor3)
  private val c3_e8 = SearchEverywhereFoundElementInfoTest("c3_e8", contributor3)


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
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val throttlingWrapper = ThrottlingListenerWrapper(mockListener)
    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted("test", listOf(contributor1, contributor2))
        oneOf(mockListener).elementsAdded(listOf(c1_e1, c1_e3, c1_e5, c1_e6, c2_e1, c2_e2, c2_e3, c2_e4, c1_e7, c1_e8))
        oneOf(mockListener).elementsRemoved(listOf(c2_e5, c2_e6))
        oneOf(mockListener).contributorFinished(contributor1, false)
        oneOf(mockListener).contributorFinished(contributor2, true)
        one(mockListener).searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, true)))
      }
    })
    with<SearchListener, Unit>(throttlingWrapper) {
      searchStarted("test", listOf(contributor1, contributor2))
      elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c1_e4))
      elementsAdded(listOf(c1_e5, c1_e6))
      contributorWaits(contributor1) //wait event 1
      elementsRemoved(listOf(c1_e2, c1_e4))
      elementsAdded(listOf(c2_e1, c2_e2, c2_e3, c2_e4))
      contributorWaits(contributor2) //wait event 2
      elementsRemoved(listOf(c2_e5, c2_e6))
      elementsAdded(listOf(c1_e7, c1_e8)) //new items should erase [wait event 1] in buffer

      contributorFinished(contributor1, false)
      contributorFinished(contributor2, true) // finish event should erase [wait event 2] in buffer

      searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, true)))
    }
    myMockery!!.assertIsSatisfied()
  }

  fun `test events order for WaitForContributorsListenerWrapper`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val wfcWrapper = WaitForContributorsListenerWrapper(mockListener, MixedSearchListModel(),
                                                        WaitForContributorsListenerWrapper.DEFAULT_WAIT_TIMEOUT_MS,
                                                        WaitForContributorsListenerWrapper.DEFAULT_THROTTLING_TIMEOUT_MS,
                                                        {"?"})

    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted("test", listOf(contributor1, contributor2))
        oneOf(mockListener).elementsAdded(listOf(c1_e1, c1_e3, c1_e5, c1_e6, c2_e1, c2_e2, c2_e3, c2_e4, c1_e7, c1_e8))
        oneOf(mockListener).elementsRemoved(listOf(c2_e5, c2_e6))
        oneOf(mockListener).contributorFinished(contributor1, false)
        oneOf(mockListener).contributorFinished(contributor2, true)
        one(mockListener).searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, true)))
      }
    })
    with<SearchListener, Unit>(wfcWrapper) {
      searchStarted("test", listOf(contributor1, contributor2))
      elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c1_e4))
      elementsAdded(listOf(c1_e5, c1_e6))
      contributorWaits(contributor1) //wait event 1
      elementsRemoved(listOf(c1_e2, c1_e4))
      elementsAdded(listOf(c2_e1, c2_e2, c2_e3, c2_e4))
      contributorWaits(contributor2) //wait event 2
      elementsRemoved(listOf(c2_e5, c2_e6))
      elementsAdded(listOf(c1_e7, c1_e8)) //new items should erase [wait event 1] in buffer

      contributorFinished(contributor1, false)
      contributorFinished(contributor2, true) // finish event should erase [wait event 2] in buffer

      searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, true)))
    }

    myMockery!!.assertIsSatisfied()
  }

  fun `test ThrottlingListenerWrapper flushes buffer on timeout`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val throttlingWrapper = ThrottlingListenerWrapper(100, mockListener)

    val executorService = Executors.newSingleThreadScheduledExecutor()
    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted("test", listOf(contributor1, contributor2))
        oneOf(mockListener).elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c2_e1, c2_e2, c2_e3))
        oneOf(mockListener).contributorWaits(contributor1)
        oneOf(mockListener).elementsRemoved(listOf(c1_e1, c1_e2))
        oneOf(mockListener).elementsAdded(listOf(c2_e4, c2_e5))
        oneOf(mockListener).contributorFinished(contributor1, false)
        oneOf(mockListener).contributorFinished(contributor2, false)
        one(mockListener).searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false)))
      }
    })

    val request1 = Runnable {
      SwingUtilities.invokeAndWait {
        with(throttlingWrapper) {
          searchStarted("test", listOf(contributor1, contributor2))
          elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c1_e4))
          elementsRemoved(listOf(c1_e4))
          contributorWaits(contributor1)
          elementsAdded(listOf(c2_e1, c2_e2, c2_e3))
        }
      }
    }

    val request2 = Runnable {
      SwingUtilities.invokeAndWait {
        with(throttlingWrapper) {
          elementsRemoved(listOf(c1_e1, c1_e2))
          elementsAdded(listOf(c2_e4, c2_e5))
          contributorFinished(contributor1, false)
          contributorFinished(contributor2, false)
          searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false)))
        }
      }
    }

    executorService.schedule(request1, 0, TimeUnit.MILLISECONDS)
    executorService.schedule(request2, 200, TimeUnit.MILLISECONDS)
    executorService.shutdown()
    awaitShutdownNonBlocking(executorService)

    myMockery!!.assertIsSatisfied()
  }

  fun `test previous search items are cleared (ThrottlingListenerWrapper)`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val throttlingWrapper = ThrottlingListenerWrapper(mockListener)
    doTestClearPreviousResults(mockListener, throttlingWrapper)
    myMockery!!.assertIsSatisfied()

  }

  fun `test previous search items are cleared (WaitForContributorsListenerWrapper)`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val wfcWrapper = WaitForContributorsListenerWrapper(mockListener, MixedSearchListModel(),
                                                        WaitForContributorsListenerWrapper.DEFAULT_WAIT_TIMEOUT_MS,
                                                        WaitForContributorsListenerWrapper.DEFAULT_THROTTLING_TIMEOUT_MS,
                                                        {"?"})
    doTestClearPreviousResults(mockListener, wfcWrapper)
    myMockery!!.assertIsSatisfied()
  }

  fun `test WaitForContributorsListenerWrapper waits for non-slow contributors`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val wfcWrapper = WaitForContributorsListenerWrapper(mockListener, MixedSearchListModel(), 2000, 100, {"?"})

    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted("test", listOf(contributor1, contributor2, contributor3))
        oneOf(mockListener).elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c2_e1, c2_e2, c2_e3))
        oneOf(mockListener).contributorFinished(contributor1, false)
        oneOf(mockListener).contributorWaits(contributor2)
        oneOf(mockListener).elementsAdded(listOf(c3_e1, c3_e2, c3_e3))
        oneOf(mockListener).contributorFinished(contributor3, false)
        oneOf(mockListener).contributorFinished(contributor2, true)
        oneOf(mockListener).searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false), Pair(contributor3, false)))
      }
    })

    val request1 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          searchStarted("test", listOf(contributor1, contributor2, contributor3))
          elementsAdded(listOf(c1_e1, c1_e2, c1_e3))
          contributorFinished(contributor1, false)
        }
      }
    }
    val request2 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          elementsAdded(listOf(c2_e1, c2_e2, c2_e3))
          contributorWaits(contributor2)
        }
      }
    }
    val request3 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          elementsAdded(listOf(c3_e1, c3_e2, c3_e3))
          contributorFinished(contributor3, false)
          contributorFinished(contributor2, true)
          searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false), Pair(contributor3, false)))
        }
      }
    }

    val executorService = Executors.newSingleThreadScheduledExecutor()
    executorService.schedule(request1, 0, TimeUnit.MILLISECONDS)
    executorService.schedule(request2, 200, TimeUnit.MILLISECONDS)
    executorService.schedule(request3, 400, TimeUnit.MILLISECONDS)
    executorService.shutdown()
    awaitShutdownNonBlocking(executorService)

    myMockery!!.assertIsSatisfied()
  }

  fun `test WaitForContributorsListenerWrapper flushes buffer on timeout`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val wfcWrapper = WaitForContributorsListenerWrapper(mockListener, MixedSearchListModel(), 500, 100, {"?"})
    val executorService = Executors.newSingleThreadScheduledExecutor()

    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted("test", listOf(contributor1, contributor2, contributor3))
        oneOf(mockListener).elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c2_e1, c2_e2, c2_e3))
        oneOf(mockListener).contributorFinished(contributor1, false)
        oneOf(mockListener).elementsAdded(listOf(c2_e4, c2_e5, c2_e6, c3_e1, c3_e2, c3_e3))
        oneOf(mockListener).contributorFinished(contributor2, false)
        oneOf(mockListener).contributorFinished(contributor3, false)
        oneOf(mockListener).searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false), Pair(contributor3, false)))
      }
    })

    val request1 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          searchStarted("test", listOf(contributor1, contributor2, contributor3))
          elementsAdded(listOf(c1_e1, c1_e2, c1_e3))
          contributorFinished(contributor1, false)
          elementsAdded(listOf(c2_e1, c2_e2, c2_e3))
        }
      }
    }

    val request2 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          elementsAdded(listOf(c2_e4, c2_e5, c2_e6))
          elementsAdded(listOf(c3_e1, c3_e2, c3_e3))
          contributorFinished(contributor2, false)
          contributorFinished(contributor3, false)
          searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false), Pair(contributor3, false)))
        }
      }
    }

    executorService.schedule(request1, 0, TimeUnit.MILLISECONDS)
    executorService.schedule(request2, 1000, TimeUnit.MILLISECONDS)
    executorService.shutdown()
    awaitShutdownNonBlocking(executorService)

    myMockery!!.assertIsSatisfied()
  }

  fun `test WaitForContributorsListenerWrapper uses buffer after non-slow contributors finished`() {
    val mockListener = myMockery!!.mock(SearchListener::class.java)
    val wfcWrapper = WaitForContributorsListenerWrapper(mockListener, MixedSearchListModel(), 500, 500, {"?"})
    val executorService = Executors.newSingleThreadScheduledExecutor()

    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted("test", listOf(contributor1, contributor2, contributor3))
        oneOf(mockListener).elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c2_e1, c2_e2, c2_e3, c3_e1, c3_e2, c3_e3, c3_e4, c3_e5, c3_e6))
        oneOf(mockListener).contributorFinished(contributor1, false)
        oneOf(mockListener).contributorFinished(contributor2, false)
        oneOf(mockListener).elementsAdded(listOf(c3_e7, c3_e8))
        oneOf(mockListener).contributorFinished(contributor3, false)
        oneOf(mockListener).searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false), Pair(contributor3, false)))
      }
    })

    with(wfcWrapper) {
      searchStarted("test", listOf(contributor1, contributor2, contributor3))
      elementsAdded(listOf(c1_e1, c1_e2, c1_e3))
      contributorFinished(contributor1, false)
      elementsAdded(listOf(c2_e1, c2_e2, c2_e3))
      contributorFinished(contributor2, false)
    }

    val request1 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          elementsAdded(listOf(c3_e1, c3_e2, c3_e3))
        }
      }
    }

    val request2 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          elementsAdded(listOf(c3_e4, c3_e5, c3_e6))
        }
      }
    }

    val request3 = Runnable {
      SwingUtilities.invokeAndWait {
        with(wfcWrapper) {
          elementsAdded(listOf(c3_e7, c3_e8))
          contributorFinished(contributor3, false)
          searchFinished(mapOf(Pair(contributor1, false), Pair(contributor2, false), Pair(contributor3, false)))
        }
      }
    }

    executorService.schedule(request1, 0, TimeUnit.MILLISECONDS)
    executorService.schedule(request2, 100, TimeUnit.MILLISECONDS)
    executorService.schedule(request3, 700, TimeUnit.MILLISECONDS)
    executorService.shutdown()
    awaitShutdownNonBlocking(executorService)

    myMockery!!.assertIsSatisfied()
  }

  private fun awaitShutdownNonBlocking(executorService: ScheduledExecutorService) = resetThreadContext {
    val eventQueue = Toolkit.getDefaultToolkit().systemEventQueue as IdeEventQueue
    while (!executorService.awaitTermination(10, TimeUnit.MILLISECONDS)) {
      val event = eventQueue.nextEvent
      eventQueue.dispatchEvent(event)
    }
  }

  private fun doTestClearPreviousResults(mockListener: SearchListener, wrapper: SearchListener) {
    myMockery!!.checking(object : Expectations() {
      init {
        oneOf(mockListener).searchStarted("test", listOf(contributor1))
        oneOf(mockListener).searchStarted("test", listOf(contributor1))
        oneOf(mockListener).elementsAdded(listOf(c1_e5, c1_e6, c1_e7, c1_e8))
        oneOf(mockListener).contributorFinished(contributor1, true)
        oneOf(mockListener).searchFinished(mapOf(Pair(contributor1, true)))
      }
    })

    with(wrapper) {
      searchStarted("test", listOf(contributor1))
      elementsAdded(listOf(c1_e1, c1_e2, c1_e3, c1_e4))

      searchStarted("test", listOf(contributor1))
      elementsAdded(listOf(c1_e5, c1_e6, c1_e7, c1_e8))
      contributorFinished(contributor1, true)
      searchFinished(mapOf(Pair(contributor1, true)))
    }
  }
}

private class SearchEverywhereFoundElementInfoTest(val name: String, contributor: SearchEverywhereContributor<*>)
  : SearchEverywhereFoundElementInfo(name, 0, contributor) {
  override fun toString(): String = name
}