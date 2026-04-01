// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.MessagePoolAdvisor.AfterEntryAddedEvent
import com.intellij.diagnostic.MessagePoolAdvisor.BeforeEntryAddedEvent
import com.intellij.diagnostic.MessagePoolAdvisor.EntryReadEvent
import com.intellij.diagnostic.MessagePoolAdvisor.PoolClearedEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.util.SlowOperations
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus

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
  private val myAdvisors: MutableList<MessagePoolAdvisor> = ContainerUtil.createLockFreeCopyOnWriteList()

  @Deprecated("use {@link #addErrorMessage(AbstractMessage)} instead ")
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

  @Deprecated("use {@link #addErrorMessage(AbstractMessage)} instead ")
  fun addIdeFatalMessage(message: AbstractMessage) {
    addErrorMessage(message)
  }

  @OptIn(DelicateCoroutinesApi::class)
  fun addErrorMessage(message: AbstractMessage): Deferred<Unit> {
    return GlobalScope.async { // must be functioning even during startup
      if (myErrors.size < MAX_POOL_SIZE) {
        doAddMessage(message)
      }
      else if (myErrors.size == MAX_POOL_SIZE) {
        doAddMessage(LogMessage(TooManyErrorsException(), null, mutableListOf<Attachment>()))
      }
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

  @Deprecated("Use MessagePoolAdvisor")
  fun addListener(listener: MessagePoolListener) {
    addAdvisor(MessagePoolAdvisorAdapter(listener))
  }

  @Deprecated("Use MessagePoolAdvisor")
  fun removeListener(listener: MessagePoolListener) {
    removeAdvisor(MessagePoolAdvisorAdapter(listener))
  }

  fun addAdvisor(advisor: MessagePoolAdvisor) {
    myAdvisors.add(advisor)
  }

  fun removeAdvisor(advisor: MessagePoolAdvisor) {
    myAdvisors.remove(advisor)
  }

  private suspend fun notifyEntryAdded(m: AbstractMessage) {
    for (it in myAdvisors) {
      it.afterEntryAdded(AfterEntryAddedEvent(m))
    }
  }

  private fun notifyPoolCleared() {
    for (it in myAdvisors) {
      it.poolCleared(PoolClearedEvent())
    }
  }

  private fun notifyEntryRead(m: AbstractMessage) {
    for (it in myAdvisors) {
      it.entryWasRead(EntryReadEvent(m))
    }
  }

  private suspend fun doAddMessage(message: AbstractMessage) {
    for (listener in myAdvisors) {
      if (!listener.beforeEntryAdded(BeforeEntryAddedEvent(message))) {
        return
      }
    }

    if (ApplicationManager.getApplication().isInternal()) {
      message.allAttachments.forEach { it.isIncluded = true }
    }

    if (shallAddSilently(message)) {
      message.setRead(true)
    }

    message.setOnReadCallback { notifyEntryRead(message) }
    myErrors.add(message)
    notifyEntryAdded(message)
  }

  class TooManyErrorsException internal constructor() : Exception(DiagnosticBundle.message("error.monitor.too.many.errors"))

  private fun shallAddSilently(message: AbstractMessage): Boolean {
    return SlowOperations.isMyMessage(message.getThrowable().message)
  }
}

private class MessagePoolAdvisorAdapter(val listener: MessagePoolListener) : MessagePoolAdvisor {
  override suspend fun beforeEntryAdded(e: BeforeEntryAddedEvent): Boolean {
    return listener.beforeEntryAdded(e.message)
  }

  override suspend fun afterEntryAdded(e: AfterEntryAddedEvent) {
    listener.newEntryAdded()
  }

  override fun poolCleared(e: PoolClearedEvent) {
    listener.poolCleared()
  }

  override fun entryWasRead(e: EntryReadEvent) {
    listener.entryWasRead()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MessagePoolAdvisorAdapter

    return listener == other.listener
  }

  override fun hashCode(): Int {
    return listener.hashCode()
  }
}