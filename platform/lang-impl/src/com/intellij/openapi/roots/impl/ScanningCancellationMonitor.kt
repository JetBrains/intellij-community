// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Internal

package com.intellij.openapi.roots.impl

import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.AttachmentFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.ThrottledLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private val LOG = Logger.getInstance(ScanningCancellationMonitor::class.java)
private val THROTTLED_LOG = ThrottledLogger(LOG, TimeUnit.MINUTES.toMillis(1))

const val SCANNING_MONITOR_ENABLED_KEY: String = "ide.scanning.cancellation.monitor"
const val SCANNING_MONITOR_GRACE_MS_KEY: String = "ide.scanning.cancellation.monitor.grace.ms"
const val SCANNING_MONITOR_MAX_REPORTS_KEY: String = "ide.scanning.cancellation.monitor.max.reports"

/** Why a [FilesScanExecutor] worker was still holding a read action after a write action asked it to stop. */
@Internal
enum class ScanningStallKind {
  /** The write-action-priority protocol never canceled the indicator. */
  NOT_CANCELED,

  /**
   * The indicator reports canceled, but the thread was never added to `threadsUnderCanceledIndicator`, so
   * `ProgressManager.checkCanceled()` cannot throw on it no matter how often it is called.
   */
  CANCELLATION_UNOBSERVED,

  /** Canceled and correctly marked; the thread simply has not reached its next cancellation check yet. */
  CANCELED_NOT_YET_NOTICED,
}

@Internal
class ScanningStallEntry(
  @JvmField val thread: Thread,
  @JvmField val indicatorPresentation: String,
  @JvmField val ageMs: Long,
  @JvmField val kind: ScanningStallKind,
  @JvmField val underCanceledIndicator: Boolean,
) {
  val isStalled: Boolean get() = kind != ScanningStallKind.CANCELED_NOT_YET_NOTICED
}

@Internal
class ScanningStallReport(
  @JvmField val writeActionName: String,
  @JvmField val writeActionPendingMs: Long,
  @JvmField val checkCanceledBehavior: String,
  @JvmField val entries: List<ScanningStallEntry>,
  @JvmField val recentWriteActions: List<String>,
) {
  val stalled: List<ScanningStallEntry> get() = entries.filter { it.isStalled }

  fun summary(): String {
    val notCanceled = stalled.count { it.kind == ScanningStallKind.NOT_CANCELED }
    val unobserved = stalled.count { it.kind == ScanningStallKind.CANCELLATION_UNOBSERVED }
    return "Scanning thread(s) did not stop for a pending write action: " +
           "$notCanceled not canceled, $unobserved with unobservable cancellation"
  }

  fun details(): String = buildString {
    append(summary()).append('\n')
    append("triggering write action: ").append(writeActionName)
      .append(", pending for ").append(writeActionPendingMs).append(" ms\n")
    append("ProgressManager.checkCanceled behavior: ").append(checkCanceledBehavior)
      .append(" (with NONE or ONLY_HOOKS the thread's indicator is never consulted)\n")
    append('\n')
    for (entry in entries) {
      append("--- ").append(entry.thread).append(" [").append(entry.thread.state).append("]\n")
      append("    holding a scan read action for ").append(entry.ageMs).append(" ms\n")
      append("    diagnosis: ").append(entry.kind).append('\n')
      append("    under canceled indicator (checkCanceled can throw): ").append(entry.underCanceledIndicator).append('\n')
      append("    indicator chain: ").append(entry.indicatorPresentation).append('\n')
      append(stackTraceOf(entry.thread))
    }
    if (recentWriteActions.isNotEmpty()) {
      append("\nrecent write actions (most recent last):\n")
      for (line in recentWriteActions) append("  ").append(line).append('\n')
    }
  }

  private fun stackTraceOf(thread: Thread): String {
    val writer = StringWriter()
    try {
      ThreadDumper.dumpCallStack(thread, writer, thread.stackTrace)
    }
    catch (e: Throwable) {
      return "    <failed to dump the stack: $e>\n"
    }
    return writer.toString()
  }
}

/**
 * Watches [FilesScanExecutor]'s workers and reacts when one of them keeps holding a read action after a write action has
 * asked it to stop.
 *
 * A pending write action makes the write-action-priority protocol cancel every scan read action, and the worker is
 * expected to abort at its next `ProgressManager.checkCanceled()`. Freeze reports show that this can fail: workers
 * registered under a canceled indicator kept running, because the thread had never been added to
 * `threadsUnderCanceledIndicator` and so `checkCanceled()` never consulted the indicator. The EDT then waited for the
 * read lock for as long as the scan happened to run.
 *
 * This monitor detects that state, reports it in full, and repairs it by re-canceling the per-item indicator, which
 * re-runs the bookkeeping that was missed. It is a safety net and a diagnostic, not a fix for the underlying defect.
 *
 * @param reporter where a report goes; overridden in tests so they can assert on the report instead of the log
 * @param graceMs how long a worker may keep the read action after the write action became pending
 */
@Internal
class ScanningCancellationMonitor(
  private val coroutineScope: CoroutineScope,
  private val tracker: ScanningWorkTracker = ScanningWorkTracker.getInstance(),
  private val reporter: (ScanningStallReport) -> Unit = ::reportStallToLog,
  private val graceMs: () -> Long = { Registry.intValue(SCANNING_MONITOR_GRACE_MS_KEY, 1000).toLong() },
) : WriteActionListener {

  private val pendingCheck = AtomicReference<Job?>()
  private val writeActionLog = WriteActionLog()
  private val reportsEmitted = AtomicInteger()

  /**
   * Incremented whenever a write action acquires the write lock. A check that sees this change knows every conflicting
   * read action must have been released, so nothing was holding the write action up.
   */
  private val writeLockAcquisitions = AtomicLong()

  override fun beforeWriteActionStart(action: Class<*>) {
    val startedAtNanos = System.nanoTime()
    writeActionLog.record("pending", action, startedAtNanos)
    if (!isEnabled() || tracker.isEmpty()) {
      return
    }
    // More than one write action can be pending at a time -- the lock only fires this for the outermost one, but two
    // threads can each be waiting to acquire -- so the check must belong to the *first* one that started waiting.
    // Re-arming on every arrival would restart the grace period each time and, while write actions keep arriving, would
    // postpone the check indefinitely: exactly the situation this monitor exists to catch.
    if (pendingCheck.get() != null) {
      return
    }
    val acquisitionsWhenArmed = writeLockAcquisitions.get()
    // The write lock is not held yet, so this must be cheap and must not throw: listener exceptions are swallowed by
    // the lock implementation. The check itself is dispatched to `Dispatchers.IO` -- never here, and never on the EDT,
    // which is the thread about to be blocked -- because reporting writes a thread dump.
    val check = coroutineScope.launch(Dispatchers.IO) {
      val self = coroutineContext[Job]
      try {
        delay(graceMs().milliseconds)
        if (writeLockAcquisitions.get() == acquisitionsWhenArmed) {
          runCheck(action.name, startedAtNanos, acquisitionsWhenArmed)
        }
      }
      finally {
        // only clear our own arming, so a check armed in the meantime is not dropped
        pendingCheck.compareAndSet(self, null)
      }
    }
    if (!pendingCheck.compareAndSet(null, check)) {
      // another thread armed a check for its own pending write action first; that one covers this one too
      check.cancel()
    }
  }

  override fun writeActionStarted(action: Class<*>) {
    writeActionLog.record("started", action, System.nanoTime())
    // Acquiring the write lock proves every conflicting read action was released, so nothing was stuck. Recorded rather
    // than acted on, so that a check armed for a *different*, still-pending write action is judged on this fact instead
    // of being silently disarmed and never re-armed -- the lock does not notify again for an already-pending action.
    writeLockAcquisitions.incrementAndGet()
    cancelPendingCheck()
  }

  override fun writeActionFinished(action: Class<*>) {
    writeActionLog.record("finished", action, System.nanoTime())
  }

  override fun afterWriteActionFinished(action: Class<*>) {
    writeActionLog.record("released", action, System.nanoTime())
  }

  private fun cancelPendingCheck() {
    pendingCheck.getAndSet(null)?.cancel()
  }

  /**
   * Classifies every read action that was already in progress when the write action became pending, reports the ones
   * that should have stopped and did not, and cancels their indicators.
   */
  private fun runCheck(writeActionName: String, writeActionStartedAtNanos: Long, acquisitionsWhenArmed: Long) {
    try {
      if (!isEnabled()) return
      val entries = tracker.activeReadActions()
        .filter { it.startedAtNanos < writeActionStartedAtNanos }
        .map { classify(it) }
      if (entries.none { it.isStalled }) {
        // either everything stopped, or the remaining workers are correctly canceled and simply have not noticed yet
        return
      }

      // Classifying takes time, and a write action can acquire the lock while it runs -- which proves every conflicting
      // read action was released, so there is nothing to report and nothing to repair. Re-reading the counter here
      // narrows that false-positive window to the classification pass instead of the whole grace period; it does not
      // close it, since the lock can still be acquired right after this read.
      if (writeLockAcquisitions.get() != acquisitionsWhenArmed) {
        return
      }

      val report = ScanningStallReport(
        writeActionName = writeActionName,
        writeActionPendingMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - writeActionStartedAtNanos),
        checkCanceledBehavior = CoreProgressManager.getCheckCanceledBehaviorName(),
        entries = entries,
        recentWriteActions = writeActionLog.snapshot(),
      )

      val maxReports = Registry.intValue(SCANNING_MONITOR_MAX_REPORTS_KEY, 10)
      if (maxReports < 0 || reportsEmitted.incrementAndGet() <= maxReports) {
        reporter(report)
      }

      // Repair, never rate limited. Canceling the per-item SensitiveProgressWrapper is always correct -- it exists only
      // so that a write action can abort this read action -- and cancel() re-runs CoreProgressManager.indicatorCanceled
      // unconditionally, which re-marks the thread when that marking was missed the first time.
      repair(entries)
    }
    catch (e: Throwable) {
      // The check runs in the monitor's own scope, and `repair` reaches ProgressManager through a blocking service
      // lookup, so a scope that dies mid-check -- application shutdown, or a test tearing its monitor down -- surfaces
      // here as a PCE. Abort instead of reporting it: logging a control-flow exception is a defect of its own, which
      // `Logger.ensureNotControlFlow` turns into a test failure.
      rethrowControlFlowException(e)
      LOG.error("Scanning cancellation monitor failed", e)
    }
  }

  private fun classify(readAction: ScanningReadAction): ScanningStallEntry {
    val indicator = readAction.indicator
    val underCanceledIndicator = CoreProgressManager.hasThreadUnderCanceledIndicator(readAction.thread)
    val kind = when {
      !indicator.isCanceled -> ScanningStallKind.NOT_CANCELED
      !underCanceledIndicator -> ScanningStallKind.CANCELLATION_UNOBSERVED
      else -> ScanningStallKind.CANCELED_NOT_YET_NOTICED
    }
    return ScanningStallEntry(
      thread = readAction.thread,
      indicatorPresentation = indicator.toString(),
      ageMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - readAction.startedAtNanos),
      kind = kind,
      underCanceledIndicator = underCanceledIndicator,
    )
  }

  private fun repair(entries: List<ScanningStallEntry>) {
    val threads = entries.filter { it.isStalled }.mapTo(HashSet()) { it.thread }
    if (threads.isEmpty()) return
    val repaired = tracker.activeReadActions().filter { it.thread in threads }
    for (readAction in repaired) {
      readAction.indicator.cancel()
    }
    // Report whether re-canceling was enough, so we learn if the repair actually works in the field.
    coroutineScope.launch(Dispatchers.IO) {
      delay(graceMs().milliseconds)
      val stillHolding = tracker.activeReadActions().count { it.thread in threads }
      if (stillHolding > 0) {
        THROTTLED_LOG.warn("$stillHolding scanning thread(s) still hold a read action after being re-canceled")
      }
    }
  }

  private fun isEnabled(): Boolean = Registry.`is`(SCANNING_MONITOR_ENABLED_KEY, true)
}

/** The platform keeps no history of write actions, so the monitor keeps a bounded one for the report. */
private class WriteActionLog {
  private val entries = ArrayDeque<String>()
  private val startNanos = System.nanoTime()

  @Synchronized
  fun record(phase: String, action: Class<*>, atNanos: Long) {
    if (entries.size >= CAPACITY) entries.removeFirst()
    entries.addLast("+${TimeUnit.NANOSECONDS.toMillis(atNanos - startNanos)}ms $phase ${action.name}" +
                    " on ${Thread.currentThread().name}")
  }

  @Synchronized
  fun snapshot(): List<String> = entries.toList()

  private companion object {
    private const val CAPACITY = 32
  }
}

private fun reportStallToLog(report: ScanningStallReport) {
  val details = report.details()
  val attachments = ArrayList<Attachment>()
  attachments.add(Attachment("scanningThreads.txt", details).also { it.isIncluded = true })

  // getThreadDumpInfo appends the progress-indicator dump (indicator -> threads, with canceled/running per indicator)
  // and the locks & actions dump, which is exactly the evidence needed next to the stacks.
  val dumpPath = try {
    PerformanceWatcher.getInstanceIfCreated()?.dumpThreads("scanningNotCancelled", true, false)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Failed to write a thread dump for the scanning cancellation report", e)
    null
  }
  if (dumpPath != null) {
    try {
      attachments.add(AttachmentFactory.createAttachment(dumpPath, false))
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to attach $dumpPath", e)
    }
  }

  LOG.error(RuntimeExceptionWithAttachments(report.summary(), details, *attachments.toTypedArray()))
}

/**
 * Owns the scope the monitor's checks run in.
 *
 * The write action listener is application-wide, so its scope has to be too. A project-scoped one would be the wrong
 * lifetime: `StartupManagerImpl.createActivityScope` makes a project activity's scope a child of that project's, so the
 * listener would be removed when the project that happened to install it closes, while other projects keep scanning.
 * Being a service also means the installation happens exactly once however many projects are opened.
 */
@Service(Service.Level.APP)
internal class ScanningCancellationMonitorService(coroutineScope: CoroutineScope) : Disposable.Default {
  init {
    val application = ApplicationManagerEx.getApplicationEx()
    application.addWriteActionListener(ScanningCancellationMonitor(coroutineScope), this)
  }
}

/**
 * Creates [ScanningCancellationMonitorService] once a project is open, since there is nothing to monitor before that.
 *
 * Not executed in unit test mode, so tests install [ScanningCancellationMonitor] themselves.
 */
internal class ScanningCancellationMonitorStarter : ProjectActivity {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || application.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (!Registry.`is`(SCANNING_MONITOR_ENABLED_KEY, true)) {
      return
    }
    serviceAsync<ScanningCancellationMonitorService>()
  }
}
