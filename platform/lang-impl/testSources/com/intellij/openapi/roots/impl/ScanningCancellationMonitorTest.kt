// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Drives [ScanningCancellationMonitor] through its own API rather than through a real scan: the interesting states are
 * races between a pooled worker and the thread draining the queue, which a test cannot schedule reliably. Registering a
 * read action directly makes every case deterministic.
 */
@TestApplication
class ScanningCancellationMonitorTest {
  private val cleanups = ArrayList<() -> Unit>()

  /** Stands in for the scope the starter passes in; cancelled after each test so no check outlives it. */
  private lateinit var monitorScope: CoroutineScope

  /** A tracker of its own, so tests neither see nor leak the state of the application-level one. */
  private lateinit var tracker: ScanningWorkTracker

  /** Opened in [tearDown], so a check parked in [GatedGrace] can never keep a thread past the test that armed it. */
  private val gates = ArrayList<GatedGrace>()

  @OptIn(DelicateCoroutinesApi::class)
  @BeforeEach
  fun setUp() {
    monitorScope = GlobalScope.childScope("ScanningCancellationMonitorTest")
    tracker = ScanningWorkTracker()
  }

  @AfterEach
  fun tearDown() {
    gates.forEach { it.open() }
    gates.clear()
    // Joining, not just cancelling: a check that outlives its test would keep calling into the monitor -- and into a
    // ProgressManager lookup that a dying scope makes throw -- while the next test is already setting itself up.
    runBlocking { monitorScope.coroutineContext.job.cancelAndJoin() }
    cleanups.asReversed().forEach { it() }
    cleanups.clear()
  }

  @Test
  fun `read action that was never canceled is reported and canceled`() {
    val wrapper = sensitiveWrapper()
    registerStuckReadAction(wrapper)

    val report = awaitReport()

    val stalled = report.stalled.single()
    assertEquals(ScanningStallKind.NOT_CANCELED, stalled.kind)
    assertTrue(wrapper.isCanceled, "the monitor must cancel the indicator it reported")
    // exercises the formatting, including dumping the stack of a thread that was never started
    assertTrue(report.details().contains("NOT_CANCELED"), report.details())
  }

  @Test
  fun `cancellation the thread cannot observe is reported`() {
    val root = ProgressIndicatorBase(false, false)
    val wrapper = SensitiveProgressWrapper(root)
    // Canceling the root makes the whole chain report canceled, while nothing marked the thread -- the state the freeze
    // reports show, in which ProgressManager.checkCanceled() can never throw on that thread.
    root.cancel()
    assertTrue(wrapper.isCanceled)
    val thread = registerStuckReadAction(wrapper)
    assertFalse(CoreProgressManager.hasThreadUnderCanceledIndicator(thread))

    val report = awaitReport()

    assertEquals(ScanningStallKind.CANCELLATION_UNOBSERVED, report.stalled.single().kind)
  }

  @Test
  fun `nothing is reported when the write action gets the lock`() {
    val wrapper = sensitiveWrapper()
    registerStuckReadAction(wrapper)

    val reports = CompletableFuture<ScanningStallReport>()
    val monitor = monitor(reports) { LONG_GRACE_MS }
    monitor.beforeWriteActionStart(javaClass)
    monitor.writeActionStarted(javaClass)

    assertNoReport(reports)
    assertFalse(wrapper.isCanceled, "a write action that got the lock must not cancel anything")
  }

  @Test
  fun `nothing is reported when no scan read action is active`() {
    val reports = CompletableFuture<ScanningStallReport>()
    monitor(reports) { 0 }.beforeWriteActionStart(javaClass)

    assertNoReport(reports)
  }

  @Test
  @RegistryKey(SCANNING_MONITOR_ENABLED_KEY, "false")
  fun `kill switch disables both reporting and repair`() {
    val wrapper = sensitiveWrapper()
    registerStuckReadAction(wrapper)

    val reports = CompletableFuture<ScanningStallReport>()
    monitor(reports) { 0 }.beforeWriteActionStart(javaClass)

    assertNoReport(reports)
    assertFalse(wrapper.isCanceled, "the monitor must not touch anything while it is switched off")
  }

  @Test
  fun `the check belongs to the first pending write action`() {
    registerStuckReadAction(sensitiveWrapper())

    val reports = CompletableFuture<ScanningStallReport>()
    val grace = gatedGrace()
    val monitor = monitor(reports, grace)
    monitor.beforeWriteActionStart(FirstWriteAction::class.java)
    // a second write action starts waiting while the first is still pending; the lock notifies for both
    monitor.beforeWriteActionStart(SecondWriteAction::class.java)
    grace.open()

    val report = reports.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    assertEquals(FirstWriteAction::class.java.name, report.writeActionName,
                 "a later pending write action must not take over the check and restart its grace period")
  }

  @Test
  fun `nothing is reported when one of two pending write actions gets the lock`() {
    val wrapper = sensitiveWrapper()
    registerStuckReadAction(wrapper)

    val reports = CompletableFuture<ScanningStallReport>()
    val grace = gatedGrace()
    val monitor = monitor(reports, grace)
    monitor.beforeWriteActionStart(FirstWriteAction::class.java)
    monitor.beforeWriteActionStart(SecondWriteAction::class.java)
    // only one of them wins the lock, which proves every conflicting read action was released
    monitor.writeActionStarted(SecondWriteAction::class.java)
    grace.open()

    assertNoReport(reports)
    assertFalse(wrapper.isCanceled)
  }

  @Test
  fun `a later pending write action can arm a check once an earlier one got the lock`() {
    registerStuckReadAction(sensitiveWrapper())

    val reports = CompletableFuture<ScanningStallReport>()
    val grace = gatedGrace()
    val monitor = monitor(reports, grace)
    monitor.beforeWriteActionStart(FirstWriteAction::class.java)
    monitor.writeActionStarted(FirstWriteAction::class.java)
    monitor.beforeWriteActionStart(SecondWriteAction::class.java)
    grace.open()

    val report = reports.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    assertEquals(SecondWriteAction::class.java.name, report.writeActionName)
  }

  @Test
  fun `report carries a bounded history of recent write actions`() {
    registerStuckReadAction(sensitiveWrapper())

    val reports = CompletableFuture<ScanningStallReport>()
    val monitor = monitor(reports) { 0 }
    repeat(200) { monitor.afterWriteActionFinished(javaClass) }
    monitor.beforeWriteActionStart(javaClass)

    val recent = reports.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).recentWriteActions
    assertTrue(recent.size in 1..64, "history must stay bounded, was ${recent.size}")
    assertTrue(recent.last().contains("pending"), recent.last())
  }

  private fun monitor(reports: CompletableFuture<ScanningStallReport>, graceMs: () -> Long): ScanningCancellationMonitor =
    ScanningCancellationMonitor(monitorScope, tracker, { reports.complete(it) }, graceMs)

  private fun gatedGrace(): GatedGrace = GatedGrace().also { gates.add(it) }

  private fun sensitiveWrapper(): SensitiveProgressWrapper = SensitiveProgressWrapper(ProgressIndicatorBase(false, false))

  /**
   * Registers a read action against a thread that was never started, so it is definitely absent from
   * `threadsUnderCanceledIndicator` and its state cannot be perturbed by anything else in the test application.
   */
  @Suppress("InstantiatingAThreadWithDefaultRunMethod")
  private fun registerStuckReadAction(indicator: SensitiveProgressWrapper): Thread {
    val thread = Thread("scanning-cancellation-monitor-test")
    val outer = tracker.register(thread, indicator)
    cleanups.add { tracker.unregister(thread, outer) }
    return thread
  }

  /** Checks immediately, since every negative case here is arranged so that the grace period cannot mask it. */
  private fun awaitReport(): ScanningStallReport {
    val reports = CompletableFuture<ScanningStallReport>()
    monitor(reports) { 0 }.beforeWriteActionStart(javaClass)
    return reports.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
  }

  private fun assertNoReport(reports: CompletableFuture<ScanningStallReport>) {
    assertThrows<TimeoutException> { reports.get(QUIET_MS, TimeUnit.MILLISECONDS) }
  }

  private companion object {
    private const val TIMEOUT_SECONDS = 30L

    /**
     * How long "nothing happened" is observed for. Meaningful because each negative case either never schedules a
     * check, schedules it [LONG_GRACE_MS] out, or -- with a [GatedGrace] already opened -- lets the check run and
     * decide on its own not to report.
     */
    private const val QUIET_MS = 500L
    private const val LONG_GRACE_MS = 60_000L
  }

  /**
   * A grace period that elapses only once the test opens the gate.
   *
   * The monitor evaluates `graceMs` inside the check coroutine, before that coroutine suspends, so parking here holds
   * every armed check until the test has finished arranging its write actions. Ordering therefore stops depending on
   * whether the test thread outruns [kotlinx.coroutines.Dispatchers.IO]. A check cancelled while parked still observes
   * the cancellation, at the `delay` that follows.
   */
  private class GatedGrace : () -> Long {
    private val gate = CountDownLatch(1)

    override fun invoke(): Long {
      assertTrue(gate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the test never opened the grace gate")
      return 0
    }

    fun open(): Unit = gate.countDown()
  }

  private class FirstWriteAction

  private class SecondWriteAction
}
