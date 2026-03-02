// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.util.SlowOperations
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/**
 * The class is for routing messages inside an IDE and shouldn't be accessed from plugins.
 * For reporting errors, see [com.intellij.openapi.diagnostic.Logger.error] methods.
 * For receiving reports, register own [com.intellij.openapi.diagnostic.ErrorReportSubmitter].
 */
@ApiStatus.Internal
object MessagePool {
  private const val MAX_POOL_SIZE = 100

  enum class State {
    NoErrors, ReadErrors, UnreadErrors
  }

  @JvmStatic
  fun getInstance(): MessagePool = this

  private val myErrors: MutableList<AbstractMessage> = ContainerUtil.createLockFreeCopyOnWriteList()
  private val myListeners: MutableList<MessagePoolListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  @Deprecated("use {@link #addIdeFatalMessage(AbstractMessage)} instead ")
  fun addIdeFatalMessage(event: IdeaLoggingEvent) {
    addIdeFatalMessage(
      if (event.data is AbstractMessage) event.data as AbstractMessage
      else LogMessage(
        event.throwable,
        event.message,
        mutableListOf<Attachment>()
      )
    )
  }

  fun addIdeFatalMessage(message: AbstractMessage) {
    if (myErrors.size < MAX_POOL_SIZE) {
      doAddMessage(message)
    }
    else if (myErrors.size == MAX_POOL_SIZE) {
      doAddMessage(LogMessage(TooManyErrorsException(), null, mutableListOf<Attachment>()))
    }
  }

  val state: State
    get() {
      if (myErrors.isEmpty()) return State.NoErrors
      for (message in myErrors) {
        if (!message.isRead) return State.UnreadErrors
      }
      return State.ReadErrors
    }

  fun getFatalErrors(includeReadMessages: Boolean, includeSubmittedMessages: Boolean): List<AbstractMessage> {
    val result = ArrayList<AbstractMessage>()
    for (message in myErrors) {
      if (!includeReadMessages && message.isRead) continue
      if (!includeSubmittedMessages && (message.isSubmitted || message.getThrowable() is TooManyErrorsException)) continue
      result.add(message)
    }
    return result
  }

  fun clearErrors() {
    for (message in myErrors) {
      message.setRead(true) // expire notifications
    }
    myErrors.clear()
    notifyPoolCleared()
  }

  fun addListener(listener: MessagePoolListener) {
    myListeners.add(listener)
  }

  fun removeListener(listener: MessagePoolListener) {
    myListeners.remove(listener)
  }

  private fun notifyEntryAdded() {
    myListeners.forEach(Consumer { it.newEntryAdded() })
  }

  private fun notifyPoolCleared() {
    myListeners.forEach(Consumer { it.poolCleared() })
  }

  private fun notifyEntryRead() {
    myListeners.forEach(Consumer { it.entryWasRead() })
  }

  private fun doAddMessage(message: AbstractMessage) {
    for (listener in myListeners) {
      if (!listener.beforeEntryAdded(message)) {
        return
      }
    }

    if (ApplicationManager.getApplication().isInternal()) {
      message.allAttachments.forEach(Consumer { it.isIncluded = true })
    }

    if (shallAddSilently(message)) {
      message.setRead(true)
    }

    message.setOnReadCallback(Runnable { notifyEntryRead() })
    myErrors.add(message)
    notifyEntryAdded()
  }

  class TooManyErrorsException internal constructor() : Exception(DiagnosticBundle.message("error.monitor.too.many.errors"))

  private fun shallAddSilently(message: AbstractMessage): Boolean {
    return SlowOperations.isMyMessage(message.getThrowable().message)
  }
}
