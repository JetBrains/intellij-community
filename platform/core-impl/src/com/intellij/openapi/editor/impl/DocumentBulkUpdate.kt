// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.util.concurrency.ThreadingAssertions
import kotlin.concurrent.Volatile

internal sealed class DocumentBulkUpdate(
  private val settings: DocumentSettings,
  private val listeners: LockFreeCOWSortedArray<DocumentListener>,
) {
  @Volatile private var isInBulkUpdate: Boolean = false
  @Volatile private var startTrace: Throwable? = null
  @Volatile private var isStatusChanging: Boolean = false

  protected abstract fun bulkUpdateStarting(hostDocument: Document, listener: DocumentListener)
  protected abstract fun bulkUpdateFinished(hostDocument: Document, listener: DocumentListener)

  fun isInBulkUpdate(): Boolean {
    return isInBulkUpdate
  }

  fun assertNotInBulkUpdate() {
    if (isInBulkUpdate()) {
      throw UnexpectedBulkUpdateStateException(startTrace)
    }
  }

  fun setBulkUpdateStatus(hostDocument: Document, status: Boolean) {
    if (settings.isWriteAccessCheckEnabled) {
      ThreadingAssertions.assertWriteIntentReadAccess() // TODO: reconsider threading assertion
    }
    if (isStatusChanging) {
      throw IllegalStateException("Detected bulk mode status update from DocumentBulkUpdateListener/DocumentListener")
    }
    if (isInBulkUpdate() == status) {
      return
    }
    isStatusChanging = true
    try {
      val exceptions = DocumentDelayedExceptions(isPceWarningEnabled())
      val listeners = getListeners()
      if (status) {
        try {
          notifyBulkUpdateStarting(listeners, hostDocument, exceptions)
        } finally {
          isInBulkUpdate = true
          startTrace = Throwable()
        }
      } else {
        isInBulkUpdate = false
        startTrace = null
        notifyBulkUpdateFinished(listeners, hostDocument, exceptions)
      }
      exceptions.rethrowPCE()
    } finally {
      isStatusChanging = false
    }
  }

  private fun notifyBulkUpdateStarting(
    listeners: Array<DocumentListener>,
    hostDocument: Document,
    exceptions: DocumentDelayedExceptions,
  ) {
    for (i in listeners.indices.reversed()) {
      try {
        bulkUpdateStarting(hostDocument, listeners[i])
      } catch (e: Throwable) {
        exceptions.register(e)
      }
    }
  }

  private fun notifyBulkUpdateFinished(
    listeners: Array<DocumentListener>,
    hostDocument: Document,
    exceptions: DocumentDelayedExceptions,
  ) {
    for (listener in listeners) {
      try {
        bulkUpdateFinished(hostDocument, listener)
      } catch (e: Throwable) {
        exceptions.register(e)
      }
    }
  }

  private fun isPceWarningEnabled(): Boolean {
    return settings.isPCEWarningEnabled()
  }

  private fun getListeners(): Array<DocumentListener> {
    return listeners.getArray()
  }

  class Elf(
    settings: DocumentSettings,
    listeners: LockFreeCOWSortedArray<DocumentListener>,
  ) : DocumentBulkUpdate(settings, listeners) {
    override fun bulkUpdateStarting(hostDocument: Document, listener: DocumentListener) {
      listener.bulkElfUpdateStarting(hostDocument)
    }
    override fun bulkUpdateFinished(hostDocument: Document, listener: DocumentListener) {
      listener.bulkElfUpdateFinished(hostDocument)
    }
  }

  class Real(
    settings: DocumentSettings,
    listeners: LockFreeCOWSortedArray<DocumentListener>,
  ) : DocumentBulkUpdate(settings, listeners) {
    override fun bulkUpdateStarting(hostDocument: Document, listener: DocumentListener) {
      listener.bulkUpdateStarting(hostDocument)
    }
    override fun bulkUpdateFinished(hostDocument: Document, listener: DocumentListener) {
      listener.bulkUpdateFinished(hostDocument)
    }
  }

  class Both(
    settings: DocumentSettings,
    listeners: LockFreeCOWSortedArray<DocumentListener>,
  ) : DocumentBulkUpdate(settings, listeners) {
    override fun bulkUpdateStarting(hostDocument: Document, listener: DocumentListener) {
      listener.bulkUpdateStarting(hostDocument)
      listener.bulkElfUpdateStarting(hostDocument)
    }
    override fun bulkUpdateFinished(hostDocument: Document, listener: DocumentListener) {
      listener.bulkUpdateFinished(hostDocument)
      listener.bulkElfUpdateFinished(hostDocument)
    }
  }
}
