// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.resetThreadContext
import com.intellij.diagnostic.EventWatcher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.ThrottledLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Ref
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.ThreadingAssertions
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

/**
 * Special UI Event Queue for runnables that interact with IJ Platform concepts, such as ModalityState and the RWI lock.
 *
 * ## Contract
 *
 * Normally, the execution on top of AWT EventQueue is fair (see the contracts of [java.awt.EventQueue]).
 * IntelliJ Platform manipulates two entities that influence the ordering of events -- modality states and non-blocking write-intent lock.
 * Hence, each event scheduled here has two additional values in its metadata: modality state and whether it runs under write-intent lock.
 * An important part of this scheduler is that write-intent lock acquisition is non-blocking: if a runnable fails to acquire write-intent lock,
 * it needs to be rescheduled to try later.
 *
 * The purpose of this class is to delay events in the way that retains ordering --
 * the main property of this scheduler is that
 * **if there are two events `A` and `B` with the same metadata, and scheduling of `A` happens-before scheduling of `B`, then `A` will be executed before `B`**
 * It is important that we do not enforce ordering guarantees between two runnables with different metadata:
 * if event `A` is non-modal and event `B` is modal, it is undefined which one will execute first.
 * Similarly, if event `A` requires lock and event `B` does not, it is undefined which one will execute first.
 *
 * ## Lock handling
 *
 * This class is an automaton with two states -- [WriteIntentLockMode.ALL] and [WriteIntentLockMode.UI_ONLY].
 * - In [WriteIntentLockMode.ALL] mode, the queue attempts to execute all runnables in order of their arrival.
 * If a runnable does not require write-intent lock, it can be executed right away;
 * if a runnable needs write-intent lock, [NonBlockingFlushQueue] acquires the lock in a non-blocking way.
 * If a lock cannot be acquired, the queue transitions into [WriteIntentLockMode.UI_ONLY]
 * and schedules a [WriteActionFinished] directive on termination of the existing write action.
 * - In [WriteIntentLockMode.UI_ONLY] mode, the queue runs only runnables that do not require write-intent lock.
 * When it reaches [WriteActionFinished], the queue transitions back to [WriteIntentLockMode.ALL].
 *
 * ## Modality handling
 *
 * Modality states also influence the ordering of events.
 * [NonBlockingFlushQueue] operates with two main queues: [writeIntentQueue] and [uiQueue].
 * When a polled runnable from each of those queues is deemed to have incorrect modality,
 * the runnables gets to respective skipped queue ([skippedWriteIntentQueue] or [skippedUiQueue]).
 * Once the modality state changes, skipped queues get appended back to the respective main queues.
 * While this might not sound optimal, modality changes are relatively rare in the IntelliJ Platform, so the overhead of processing is negligible.
 *
 * ## Ordering guarantees
 *
 * The runnables are scheduled to the main queues in order of their arrival.
 * The processing happens only on one thread, which ensures fairness of execution.
 */
@ApiStatus.Internal
class NonBlockingFlushQueue(private val threadingSupport: ThreadingSupport) {

  companion object {
    private val LOG = Logger.getInstance(NonBlockingFlushQueue::class.java)
    private val THROTTLED_LOG: ThrottledLogger = ThrottledLogger(LOG, TimeUnit.MINUTES.toMillis(1))
  }

  /**
   * The main queue for runnables requiring write-intent lock.
   */
  private val writeIntentQueue: BulkArrayQueue<RunnableInfo> = BulkArrayQueue()

  /**
   * Runnables, which require write-intent lock, but that were skipped due to incompatible modality states.
   */
  private val skippedWriteIntentQueue: Ref<ObjectArrayList<RunnableInfo>> = Ref(ObjectArrayList(100))

  /**
   * The main queue for runnables not requiring write-intent lock.
   */
  private val uiQueue: BulkArrayQueue<FlushQueueCommand> = BulkArrayQueue()

  /**
   * Runnables, which do not require write-intent lock, but that were skipped due to incompatible modality states.
   */
  private val skippedUiQueue: Ref<ObjectArrayList<RunnableInfo>> = Ref(ObjectArrayList(100))

  /**
   * A monitor for interaction with [writeIntentQueue], [uiQueue] and [FLUSH_SCHEDULED].
   */
  private val lockObject = Any()

  /**
   * We want to have as good latency guarantees for the executed events as possible.
   * For this, we assign a monotonically increasing counter to each event and use it to determine the order of execution.
   * If event A has a smaller associated counter than event B, and both A and B are allowed to be executed, then A will be executed first.
   * This ensures that some late UI-only runnable does not run before all earlier WI runnables
   */
  private val timeCounter = AtomicLong()

  /**
   * Protection against too frequent scheduling requests.
   */
  private var FLUSH_SCHEDULED: Boolean = false

  /**
   * Requires lock on [lockObject]
   */
  private fun setFlushScheduledGuard(value: Boolean) {
    FLUSH_SCHEDULED = value
  }

  /**
   * Requires lock on [lockObject]
   */
  private fun getFlushScheduledGuard(): Boolean {
    return FLUSH_SCHEDULED
  }

  /**
   * States of [NonBlockingFlushQueue]
   */
  private enum class WriteIntentLockMode {
    ALL,
    UI_ONLY,
  }

  private sealed interface FlushQueueCommand {
    val creationTime: Long
  }

  private class RunnableInfo(
    val runnable: Runnable,
    val modalityState: ModalityState,
    val isExpired: Condition<*>,
    val needWriteIntent: Boolean,
    override val creationTime: Long,
    val queuedTimeNs: Long,
    /** How many items were in queue at the moment this item was enqueued  */
    val queueSize: Int,
    // this field is not protected by intention. It gets mutated only on EDT
    var wasInSkippedItems: Boolean,
  ): FlushQueueCommand {
    override fun toString(): String {
      return "RunnableInfo[modalityState=$modalityState, needWi=$needWriteIntent, runnable=$runnable]"
    }
  }

  /**
   * Special directive sent at the end of write action.
   * When [com.intellij.openapi.application.impl.NonBlockingFlushQueue] encounters this directive,
   * it transitions to the state which allows processing of skipped WI events.
   */
  private class WriteActionFinished(override val creationTime: Long): FlushQueueCommand {
    override fun toString(): String {
      return "WriteActionFinished"
    }
  }

  private val FLUSH_NOW: ContextAwareRunnable = ContextAwareRunnable { flushNow() }

  /**
   * Current state of [NonBlockingFlushQueue].
   * Can be changed only on EDT
   */
  private var currentWriteIntentLockMode: WriteIntentLockMode = WriteIntentLockMode.ALL

  private fun pollNextEvent(): RunnableInfo? {
    ThreadingAssertions.assertEventDispatchThread()

    val currentModality = LaterInvocator.getCurrentModalityState()
    while (true) {
      var skippedQueue: Ref<ObjectArrayList<RunnableInfo>>
      var selectedQueue: BulkArrayQueue<*>
      val incomingInfo = synchronized(lockObject) {
        val topUiRunnable = uiQueue.peekFirst()
        val topWiRunnable = if (currentWriteIntentLockMode == WriteIntentLockMode.ALL) writeIntentQueue.peekFirst() else null
        if (topUiRunnable == null && topWiRunnable == null) {
          // the queue is effectively empty
          return null
        }
        else if (topUiRunnable != null && topWiRunnable == null) {
          skippedQueue = skippedUiQueue
          selectedQueue = uiQueue
          topUiRunnable
        } else if (topUiRunnable == null) {
          // topWiRunnable != null
          skippedQueue = skippedWriteIntentQueue
          selectedQueue = writeIntentQueue
          topWiRunnable
        } else if (topWiRunnable == null) {
          // topUiRunnable != null
          skippedQueue = skippedUiQueue
          selectedQueue = uiQueue
          topUiRunnable
        } else {
          // both not null; select first arrived
          if (topUiRunnable.creationTime < topWiRunnable.creationTime) {
            skippedQueue = skippedUiQueue
            selectedQueue = uiQueue
            topUiRunnable
          } else {
            skippedQueue = skippedWriteIntentQueue
            selectedQueue = writeIntentQueue
            topWiRunnable
          }
        }
      } ?: return null

      when (incomingInfo) {
        is WriteActionFinished -> {
          // there is a directive that write action has finished; it means that we can try to run skipped write actions again
          require(currentWriteIntentLockMode == WriteIntentLockMode.UI_ONLY) {
            "Write action finished, but FlushQueue is unexpectedly allowed to run all runnables"
          }
          if (threadingSupport.isWriteActionPending() || threadingSupport.isWriteActionInProgress()) {
            threadingSupport.runWhenWriteActionIsCompleted {
              requestFlush()
            }
            return null
          } else {
            synchronized(lockObject) {
              uiQueue.pollFirst() // now we remove WriteActionFinished from the queue
            }
            currentWriteIntentLockMode = WriteIntentLockMode.ALL
          }
        }
        is RunnableInfo -> {
          if (!currentModality.accepts(incomingInfo.modalityState)) {
            // Current modality is not acceptable; we must send such event to the Skipped Modality Queue
            skippedQueue.get().add(incomingInfo)
            incomingInfo.wasInSkippedItems = true
            synchronized(lockObject) {
              selectedQueue.pollFirst() // remove the skipped runnable
            }
            continue
          }
          if (incomingInfo.isExpired.value(null)) {
            // this runnable is expired; let's continue searching for a suitable one
            synchronized(lockObject) {
              selectedQueue.pollFirst() // remove the skipped runnable
            }
            continue
          }
          requestFlush() // in case someone wrote "invokeLater { UIUtil.dispatchAllInvocationEvents(); }"
          return incomingInfo
        }
      }
    }
  }

  private fun reincludeSkippedItems(list: Ref<ObjectArrayList<RunnableInfo>>, mainQueue: BulkArrayQueue<in RunnableInfo>) {
    ThreadingAssertions.assertEventDispatchThread()
    val size = list.get().size
    if (size != 0) {
      synchronized(lockObject) {
        mainQueue.bulkEnqueueFirst(list.get())
      }
      if (size < 100) {
        list.get().clear()
      } else {
        list.set(ObjectArrayList(100))
      }
    }
    requestFlush()
  }

  fun requestFlush() {
    synchronized(lockObject) {
      if (getFlushScheduledGuard()) {
        return
      }
      if (uiQueue.isEmpty && (currentWriteIntentLockMode == WriteIntentLockMode.UI_ONLY || writeIntentQueue.isEmpty)) {
        // so the queue is effectively empty
        return
      }
      setFlushScheduledGuard(true)
    }

    SwingUtilities.invokeLater(FLUSH_NOW)
  }

  private fun flushNow() {
    ThreadingAssertions.assertEventDispatchThread()

    synchronized(lockObject) {
      setFlushScheduledGuard(false)
    }

    val startTime = System.nanoTime()
    while (true) {
      val nextRunnable = pollNextEvent()
      if (nextRunnable == null) {
        // no more runnables to run...
        break
      }

      runNextEvent(nextRunnable)

      // we check if we want to abort only if a blocking event was executed
      // other UI events are considered quick enough
      if (InvocationUtil.priorityEventPending() || System.nanoTime() - startTime > 5_000_000) {
        requestFlush()
        break
      }
    }
  }

  @Suppress("IncorrectCancellationExceptionHandling")
  private fun runNextEvent(nextRunnable: RunnableInfo) {
    val watcher = EventWatcher.getInstanceOrNull()
    val waitingFinishedNs = System.nanoTime()
    try {
      if (nextRunnable.needWriteIntent) {
        require(currentWriteIntentLockMode == WriteIntentLockMode.ALL) {
          "Execution of Write-Intent-Locked runnables is not allowed in UI_ONLY mode"
        }
        // let's try to execute the runnable
        val success = !(threadingSupport.isWriteActionPending() || threadingSupport.isWriteActionInProgress()) && threadingSupport.tryRunWriteIntentReadAction {
          synchronized(lockObject) {
            writeIntentQueue.pollFirst() // remove runnable from writeIntentQueue
          }
          resetThreadContext {
            val progressManager = ProgressManager.getInstanceOrNull()
            if (progressManager != null) {
              progressManager.computePrioritized<Unit, Throwable> {
                nextRunnable.runnable.run()
              }
            } else {
              nextRunnable.runnable.run()
            }
          }
        }
        if (!success) {
          // we failed, which means that there is a background write action;
          // now we can to transition to the UI_ONLY state and execute only non-locking runnables
          currentWriteIntentLockMode = WriteIntentLockMode.UI_ONLY
          threadingSupport.runWhenWriteActionIsCompleted {
            synchronized(lockObject) {
              uiQueue.enqueue(WriteActionFinished(timeCounter.getAndIncrement()))
              requestFlush()
            }
          }
        }
      } else {
        synchronized(lockObject) {
          uiQueue.pollFirst() // we are going to run this runnable; let's remove it from the queue
        }
        // no write-intent required; we can execute this directly
        resetThreadContext {
          nextRunnable.runnable.run()
        }
      }
    } catch (_: ProcessCanceledException) {
      // ignored
    } catch (e: Throwable) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ExceptionUtil.rethrow(e)
      }
      if (!Logger.shouldRethrow(e)) {
        LOG.error(e)
      }
    } finally {
      reportStatistics(watcher, waitingFinishedNs, nextRunnable)
    }
  }

  private fun reportStatistics(watcher: EventWatcher?, waitingFinishedNs: Long, runnableInfo: RunnableInfo) {
    if (watcher == null) return
    val runnable: Runnable = runnableInfo.runnable
    val executionFinishedNs = System.nanoTime()
    val waitedInQueueNs: Long = waitingFinishedNs - runnableInfo.queuedTimeNs
    val executionDurationNs = executionFinishedNs - waitingFinishedNs


    //RC: ExceptionAnalyzer reports negative values here, but it is not clear there do they come from.
    //    The reasons I could think of now are:
    //    1) oddities of .nanoTime() behavior under different CPU power-saving modes
    //    2) changing .nanoTime() origin due to thead being relocated to another CPU
    //    3) long overflow in (end-start) expression.
    //    those are straightforward reasons, but 1-2 was mostly solved (it seems to me) in a modern
    //    hardware/software, and 3 is hard to expect in our use-cases. Hence, negative values could be
    //    due to some other code bug I don't see right now. Safeguarding here prevents errors down the
    //    stack, but it also shifts value statistics
    val waitedTimeInQueueNs_safe = if (waitedInQueueNs >= 0) waitedInQueueNs else 0
    val executionDurationNs_safe = if (executionDurationNs >= 0) executionDurationNs else 0

    watcher.runnableTaskFinished(runnable,
                                 waitedTimeInQueueNs_safe,
                                 runnableInfo.queueSize,
                                 executionDurationNs_safe, runnableInfo.wasInSkippedItems)

    if (waitedInQueueNs < 0 || executionDurationNs < 0) {
      //maybe logs give us some hints about why the values are negative:
      THROTTLED_LOG.info("waitedInQueueNs($waitedInQueueNs) | executionDurationNs($executionDurationNs) is negative -> unexpected state")
    }
  }

  /**
   * Since the modality states are different now, we need to reevaluate the decisions about skipped runnables
   */
  fun onModalityChanged() {
    ThreadingAssertions.assertEventDispatchThread()
    reincludeSkippedItems(skippedUiQueue, uiQueue)
    reincludeSkippedItems(skippedWriteIntentQueue, writeIntentQueue)
  }

  fun purgeExpiredItems() {
    ThreadingAssertions.assertEventDispatchThread()
    synchronized(lockObject) {
      skippedUiQueue.get().removeAll { it.isExpired.value(null) }
      skippedWriteIntentQueue.get().removeAll { it.isExpired.value(null) }
      writeIntentQueue.removeAll { it.isExpired.value(null) }
      uiQueue.removeAll { it is RunnableInfo && it.isExpired.value(null) }
      requestFlush()
    }
  }

  /**
   * Adds [runnable] to the queue.
   */
  fun push(modalityState: ModalityState, runnable: Runnable, isRunningUnderWriteIntent: Boolean, isExpired: Condition<*>) {
    val creationTime = timeCounter.getAndIncrement()
    val stamp = System.nanoTime()
    synchronized(lockObject) {
      val queueSize = writeIntentQueue.size() + uiQueue.size() // logically, this element goes to the end of the queue
      val info = RunnableInfo(runnable, modalityState, isExpired, isRunningUnderWriteIntent, creationTime, stamp, queueSize, false)
      if (isRunningUnderWriteIntent) {
        writeIntentQueue.enqueue(info)
      } else {
        uiQueue.enqueue(info)
      }
      requestFlush()
    }
  }

  override fun toString(): String {
    return "NonBlockingFlushQueue(currentWriteIntentLockMode=$currentWriteIntentLockMode, wiQueue=${writeIntentQueue.size()} elements, uiQueue=${uiQueue.size()}, skippedWriteIntentQueue=${skippedWriteIntentQueue.get().size} elements, skippedUiQueue=${skippedUiQueue.get().size} elements; flush scheduled: $FLUSH_SCHEDULED)"
  }

  fun isFlushNow(runnable: Runnable): Boolean {
    return runnable === FLUSH_NOW
  }
}