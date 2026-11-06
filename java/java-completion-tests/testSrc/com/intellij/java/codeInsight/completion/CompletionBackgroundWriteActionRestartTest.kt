// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A background write action cancels an auto-popup completion that has no items yet. The completion must restart and show the lookup.
 *
 * The write action starts on a background thread, so the restart listener of `BgCalculation` queues the restart and cancels the indicator.
 * A state delegate of the indicator blocks the cancellation until the contributor thread has queued the stop event.
 * If the listener cancels before it queues the restart, the stop event comes first, hides the empty lookup and expires the restart.
 */
class CompletionBackgroundWriteActionRestartTest : JavaCompletionAutoPopupTestCase() {
  fun testBackgroundWriteActionRestartsEmptyAutoPopup() {
    myFixture.configureByText("a.java", "class C { void foo() { <caret> } }")
    val state = RestartState()
    registerContributor(state)
    val writer = startBackgroundWriter(state)

    type("x")

    assertTrue("The background write action did not finish", writer.done.await(30, TimeUnit.SECONDS))
    assertTrue("The cancellation did not wait for the stop event", writer.stopQueued)
    assertNotNull("The restart did not run", lookup)
    assertContainsElements(myFixture.lookupElementStrings!!, RESTART_ITEM)
  }

  // Keep the lambdas out of the test method. The JUnit 3 runner rejects the synthetic `test…$lambda` methods.

  private fun registerContributor(state: RestartState) {
    BlockingContributor.sharedState = state
    Disposer.register(testRootDisposable) { BlockingContributor.sharedState = null }
    val ep = CompletionContributorEP("any", BlockingContributor::class.java.name, DefaultPluginDescriptor("IJPL-260786-test"))
    CompletionContributor.EP.point.registerExtension(ep, LoadingOrder.FIRST, testRootDisposable)
  }

  private fun startBackgroundWriter(state: RestartState): Writer {
    val writer = Writer()
    writer.thread = thread(name = "IJPL-260786-bg-writer") {
      state.contributorEntered.await(30, TimeUnit.SECONDS)
      val indicator = runInEdtAndGet { CompletionServiceImpl.currentCompletionProgressIndicator }!!
      indicator.addStateDelegate(StopAwaitingDelegate(writer))
      runBlocking {
        backgroundWriteAction { }
      }
      writer.done.countDown()
    }
    Disposer.register(testRootDisposable) { writer.thread!!.join(TimeUnit.SECONDS.toMillis(30)) }
    return writer
  }

  private class RestartState {
    val contributorEntered: CountDownLatch = CountDownLatch(1)

    @Volatile
    var firstPass: Boolean = true
  }

  private class Writer {
    @Volatile
    var thread: Thread? = null

    @Volatile
    var stopQueued: Boolean = false

    val done: CountDownLatch = CountDownLatch(1)
  }

  /**
   * Blocks the first cancellation until the contributor thread leaves [CompletionProgressIndicator.stop].
   * The restart listener makes the first cancellation. [CompletionProgressIndicator.stop] queues the stop event as its last step.
   * The EDT cannot run the stop event during the wait, because the background write action holds the write-intent lock.
   */
  private class StopAwaitingDelegate(private val writer: Writer) : AbstractProgressIndicatorExBase() {
    private val armed = AtomicBoolean(true)
    private val stopped = CountDownLatch(1)

    @Volatile
    private var contributorThread: Thread? = null

    override fun stop() {
      contributorThread = Thread.currentThread()
      stopped.countDown()
    }

    override fun cancel() {
      if (!armed.compareAndSet(true, false)) return
      if (!stopped.await(30, TimeUnit.SECONDS)) return
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
      while (contributorThread!!.stackTrace.any(::isIndicatorStop)) {
        if (System.nanoTime() > deadline) return
        Thread.sleep(1)
      }
      writer.stopQueued = true
    }

    private fun isIndicatorStop(frame: StackTraceElement): Boolean =
      frame.className == CompletionProgressIndicator::class.java.name && frame.methodName == "stop"
  }

  /**
   * On the first pass, adds no item and runs until the cancellation. On the restart pass, adds [RESTART_ITEM].
   *
   * It is [DumbAware], so it also runs in the dumb modes of `JavaCompletionTestSuite`.
   */
  private class BlockingContributor : CompletionContributor(), DumbAware {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
      val state = sharedState ?: return
      if (!state.firstPass) {
        result.addElement(LookupElementBuilder.create(RESTART_ITEM))
        return
      }
      state.firstPass = false
      state.contributorEntered.countDown()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
      while (System.nanoTime() < deadline) {
        ProgressManager.checkCanceled()
        Thread.sleep(10)
      }
    }

    companion object {
      @JvmStatic
      @Volatile
      var sharedState: RestartState? = null
    }
  }
}

private const val RESTART_ITEM = "xRestartItem"
