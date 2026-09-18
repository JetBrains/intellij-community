// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase
import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Monolith reproduction for IJPL-247940.
 *
 * A completion pass runs its contributors inside a non-write-priority read action
 * (`CompletionThreading.tryReadOrCancel`). The only thing that cancels such a
 * pass when a write action starts is the [com.intellij.codeInsight.completion.CompletionPhase.BgCalculation] listener,
 * which is armed only when the phase reaches `BgCalculation`. During the explicit/synchronous completion path
 * ([CodeCompletionHandlerBase.trySynchronousCompletion]) there is a window — phase `Synchronous`, read lock held by
 * the contributors — before the phase flips to `BgCalculation` and the listener exists. `beforeWriteActionStart`
 * fires only once, so a background write action that announces itself in that window parks behind the read lock and
 * is never cancelled: it is starved for the whole pass.
 *
 * The fix makes `BgCalculation` proactively cancel the indicator when a write action is already pending
 * (`ApplicationManagerEx.isWriteActionPending`) at construction time, which is
 * exactly the case for a writer parked since the `Synchronous` window.
 */
class CompletionWriteActionStarvationTest : JavaCompletionAutoPopupTestCase() {

  fun testBackgroundWriteActionNotStarvedDuringSynchronousPhase() {
    // Widen the synchronous phase so the background writer reliably announces itself (beforeWriteActionStart) while
    // the phase is still `Synchronous` and the contributor read lock is held, before the flip to `BgCalculation`.
    // the default is currently 2000, but we'd better not rely on it.
    CodeCompletionHandlerBase.setAutoInsertTimeout(1000, testRootDisposable)

    myFixture.configureByText("a.java", "class C { void foo() { <caret> } }")

    val state = StarvingState()
    registerStarvingContributor(state)

    val writer = Writer()
    startBackgroundWriter(state, writer)

    try {
      runExplicitCompletion()

      // With the fix, `BgCalculation` sees the pending writer, cancels the indicator, the read lock is released and
      // the write action runs (well under a second). Without the fix the writer is starved until the contributor
      // gives up on its own (the 20s safety cap in StarvingContributor), so this await times out.
      assertTrue(
        "Background write action was starved for the whole completion pass (IJPL-247940)",
        writer.done.await(15, TimeUnit.SECONDS),
      )
      assertTrue("Background write action did not run", writer.executed.get())
    }
    finally {
      writer.thread?.join(TimeUnit.SECONDS.toMillis(5))
      forceFinishCompletion()
    }
  }

  // NB: keep all lambdas out of the `test*` method itself — Kotlin compiles them to synthetic `test…$lambda$N`
  // methods that the JUnit3 runner picks up and rejects as "not public".

  private fun registerStarvingContributor(state: StarvingState) {
    StarvingContributor.sharedState = state
    Disposer.register(testRootDisposable) { StarvingContributor.sharedState = null }
    val pluginDescriptor = DefaultPluginDescriptor("IJPL-247940-test")
    val ep = CompletionContributorEP("any", StarvingContributor::class.java.name, pluginDescriptor)
    CompletionContributor.EP.point.registerExtension(ep, LoadingOrder.FIRST, testRootDisposable)
  }

  private fun startBackgroundWriter(state: StarvingState, writer: Writer) {
    writer.thread = thread(name = "IJPL-247940-bg-writer", start = true) {
      runBlocking {
        // Wait until the contributor holds the read lock in the `Synchronous` phase.
        state.enteredReadAction.await()
        // `beforeWriteActionStart` fires here while the phase is still `Synchronous` (no restart listener yet); the
        // write action then parks on the write permit behind the read lock held by the contributor.
        backgroundWriteAction { writer.executed.set(true) }
        writer.done.countDown()
      }
    }
  }

  private fun runExplicitCompletion() {
    // Explicit completion takes the synchronous path (CodeCompletionHandlerBase#trySynchronousCompletion):
    // phase `Synchronous` -> contributors run under the read lock -> after the sync timeout -> `BgCalculation`.
    // The fix cancels the in-flight pass mid-way; tolerate the resulting cancellation surfacing here.
    try {
      myFixture.completeBasic()
    }
    catch (_: Throwable) {
      // The pass is cancelled by the write-action restart; the meaningful assertion is on the writer below.
    }
  }

  private fun forceFinishCompletion() {
    // The fix cancels the indicator and reschedules a fresh completion; quiesce it deterministically so the
    // restart churn does not leak past the test.
    ApplicationManager.getApplication().invokeAndWait {
      LookupManager.getInstance(project).hideActiveLookup()
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion)
    }
  }

  private class Writer {
    @Volatile
    var thread: Thread? = null
    val executed: AtomicBoolean = AtomicBoolean(false)
    val done: CountDownLatch = CountDownLatch(1)
  }

  private class StarvingState {
    val enteredReadAction: CountDownLatch = CountDownLatch(1)
    val firstPass: AtomicBoolean = AtomicBoolean(true)
  }

  /**
   * Stands in for a slow contributor (e.g. the split frontend contributor blocking on a backend RPC) that holds the
   * non-write-priority read lock across the `Synchronous` window. It releases the lock as soon as the indicator is
   * cancelled, which is what the fix triggers.
   *
   * [DumbAware] so it also runs (and the test reproduces) in the dumb indexing modes that `JavaCompletionTestSuite`
   * exercises — non-dumb-aware contributors are filtered out in dumb mode and would never hold the read lock.
   */
  private class StarvingContributor : CompletionContributor(), DumbAware {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      val state = sharedState ?: return
      // Only the first pass parks; the restart triggered by the cancellation must complete normally.
      if (!state.firstPass.compareAndSet(true, false)) return
      // Add an element so the cancelled pass is not an empty lookup (keeps completion teardown on its normal path).
      result.addElement(LookupElementBuilder.create("zzStarvationMarker"))
      state.enteredReadAction.countDown()
      val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20)
      while (System.currentTimeMillis() < deadline) {
        ProgressManager.checkCanceled() // throws once the indicator is cancelled, unwinding the read action
        Thread.sleep(10)
      }
    }

    companion object {
      @JvmStatic
      @Volatile
      var sharedState: StarvingState? = null
    }
  }
}
