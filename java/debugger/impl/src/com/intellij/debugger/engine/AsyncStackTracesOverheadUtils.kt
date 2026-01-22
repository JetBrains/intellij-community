// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.statistics.DebuggerStatistics
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.ObjectReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private class LastSessionPauseListener : XDebugSessionListener {
  private val isPaused = MutableStateFlow(false)
  private val startNs = System.nanoTime()

  override fun sessionPaused() {
    isPaused.value = true
  }

  override fun sessionResumed() {
    isPaused.value = false
  }

  @OptIn(FlowPreview::class)
  suspend fun awaitNoPauseFor(duration: Duration) {
    isPaused.debounce(duration).first { !it }
  }

  fun passedSinceSessionStartMs(): Long {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
  }
}

private val lastSessionPauseListenerKey = Key.create<LastSessionPauseListener>("lastSessionPauseListenerKey")

internal fun initializeLastSessionPauseListener(process: DebugProcessImpl) {
  val xDebugSession = process.session.xDebugSession ?: return
  val listener = LastSessionPauseListener()
  process.putUserData(lastSessionPauseListenerKey, listener)
  xDebugSession.addSessionListener(listener)
}

@OptIn(FlowPreview::class)
internal fun showOverheadNotification(process: DebugProcessImpl, overhead: ObjectReference) {
  val xDebugSession = process.session.xDebugSession as? XDebugSessionImpl ?: return
  val cs = xDebugSession.coroutineScope
  val managerThread = DebuggerManagerThreadImpl.getCurrentThread()
  cs.launch(Dispatchers.EDT) {
    val project = xDebugSession.project
    val listener = process.getUserData(lastSessionPauseListenerKey)
    val passedSinceSessionStartMs = listener?.passedSinceSessionStartMs() ?: -1
    DebuggerStatistics.logAgentOverheadDetected(project, passedSinceSessionStartMs)
    if (listener != null) {
      // we don't want to show notification if user is actively debugging
      listener.awaitNoPauseFor(3.seconds)
    }

    if (process.isDetached) return@launch
    val title = JavaDebuggerBundle.message("async.stack.traces.overhead.title")
    val content = JavaDebuggerBundle.message("async.stack.traces.overhead.description")
    val notification = Notification("AsyncStackTraces", title, content, NotificationType.WARNING)
    notification.addAction(DumbAwareAction.create(JavaDebuggerBundle.message("async.stack.traces.overhead.throttle.button")) {
      notification.expire()
      DebuggerStatistics.logAgentOverheadNotificationThrottlingEnabled(project)
      executeOnDMT(managerThread) {
        val field = DebuggerUtils.findField(overhead.referenceType(), "throttleWhenOverhead")
        if (field != null) {
          overhead.setValue(field, overhead.virtualMachine().mirrorOf(true))
        }
      }
    })
    notification.addAction(DumbAwareAction.create(JavaDebuggerBundle.message("async.stack.traces.overhead.disable.button")) {
      notification.expire()
      DebuggerStatistics.logAgentOverheadNotificationAgentDisabled(project)
      executeOnDMT(managerThread) {
        DebuggerUtilsEx.setStaticBooleanField(process, AsyncStacksUtils.CAPTURE_STORAGE_CLASS_NAME, "ENABLED", false)
      }
    })
    notification.addAction(DumbAwareAction.create(JavaDebuggerBundle.message("async.stack.traces.overhead.ignore.button")) {
      notification.expire()
      DebuggerStatistics.logAgentOverheadNotificationDismissed(project)
    })
    val processListener = object : DebugProcessListener {
      override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
        notification.expire()
      }
    }
    process.addDebugProcessListener(processListener)
    notification.whenExpired {
      process.removeDebugProcessListener(processListener)
    }
    NotificationsManager.getNotificationsManager().showNotification(notification, process.project)
    DebuggerStatistics.logAgentOverheadNotificationShown(project)
  }
}
