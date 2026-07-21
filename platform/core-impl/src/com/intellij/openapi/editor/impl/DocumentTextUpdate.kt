// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ShutDownTracker
import kotlin.concurrent.Volatile

internal sealed class DocumentTextUpdate(
  private val settings: DocumentSettings,
  private val listeners: LockFreeCOWSortedArray<DocumentListener>,
) {
  @Volatile private var isInTextUpdate: Boolean = false

  protected abstract fun beforeDocumentChange(listener: DocumentListener, changeEvent: DocumentEvent, revertingEvent: DocumentEvent?)
  protected abstract fun documentChanged(listener: DocumentListener, changeEvent: DocumentEvent, revertedEvent: DocumentEvent?)

  fun isInTextUpdate(): Boolean {
    return isInTextUpdate
  }

  fun <T> withFiringTextUpdate(
    changeEvent: DocumentEvent,
    revertedEvent: DocumentEvent?,
    action: () -> T,
  ): T {
    if (ShutDownTracker.isShutdownStarted()) {
      return action()
    }
    val exceptions = DocumentDelayedExceptions(isPceWarningEnabled())
    val listeners = getListeners()
    var result: T? = null
    ProgressManager.getInstance().executeNonCancelableSection {
      notifyBeforeTextChange(listeners, changeEvent, revertedEvent, exceptions)
      isInTextUpdate = true
      try {
        result = action()
        notifyTextChanged(listeners, changeEvent, revertedEvent, exceptions)
      } finally {
        isInTextUpdate = false
      }
    }
    exceptions.rethrowPCE()
    return checkNotNull(result) {
      "Failed to update text"
    }
  }

  private fun notifyBeforeTextChange(
    listeners: Array<DocumentListener>,
    changeEvent: DocumentEvent,
    revertedEvent: DocumentEvent?,
    exceptions: DocumentDelayedExceptions,
  ) {
    for (i in listeners.indices.reversed()) {
      try {
        beforeDocumentChange(listeners[i], changeEvent, revertedEvent)
      } catch (e: Throwable) {
        exceptions.register(e)
      }
    }
  }

  private fun notifyTextChanged(
    listeners: Array<DocumentListener>,
    changeEvent: DocumentEvent,
    revertedEvent: DocumentEvent?,
    exceptions: DocumentDelayedExceptions,
  ) {
    for (listener in listeners) {
      try {
        documentChanged(listener, changeEvent, revertedEvent)
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
  ) : DocumentTextUpdate(settings, listeners) {
    override fun beforeDocumentChange(
      listener: DocumentListener,
      changeEvent: DocumentEvent,
      revertingEvent: DocumentEvent?,
    ) {
      listener.beforeElfDocumentChange(changeEvent, revertingEvent)
    }
    override fun documentChanged(
      listener: DocumentListener,
      changeEvent: DocumentEvent,
      revertedEvent: DocumentEvent?,
    ) {
      listener.elfDocumentChanged(changeEvent, revertedEvent)
    }
  }

  class Real(
    settings: DocumentSettings,
    listeners: LockFreeCOWSortedArray<DocumentListener>,
  ) : DocumentTextUpdate(settings, listeners) {
    override fun beforeDocumentChange(
      listener: DocumentListener,
      changeEvent: DocumentEvent,
      revertingEvent: DocumentEvent?,
    ) {
      listener.beforeDocumentChange(changeEvent)
    }
    override fun documentChanged(
      listener: DocumentListener,
      changeEvent: DocumentEvent,
      revertedEvent: DocumentEvent?,
    ) {
      listener.documentChanged(changeEvent)
    }
  }

  class Both(
    settings: DocumentSettings,
    listeners: LockFreeCOWSortedArray<DocumentListener>,
  ) : DocumentTextUpdate(settings, listeners) {
    override fun beforeDocumentChange(
      listener: DocumentListener,
      changeEvent: DocumentEvent,
      revertingEvent: DocumentEvent?,
    ) {
      listener.beforeDocumentChange(changeEvent)
      listener.beforeElfDocumentChange(changeEvent, revertingEvent)
    }
    override fun documentChanged(
      listener: DocumentListener,
      changeEvent: DocumentEvent,
      revertedEvent: DocumentEvent?,
    ) {
      listener.elfDocumentChanged(changeEvent, revertedEvent)
      listener.documentChanged(changeEvent)
    }
  }
}
