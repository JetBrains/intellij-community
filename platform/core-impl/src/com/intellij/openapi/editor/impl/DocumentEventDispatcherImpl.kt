// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEventDispatcher
import com.intellij.openapi.editor.ex.DocumentFullUpdateListener
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.EditReadOnlyListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import java.beans.PropertyChangeListener
import kotlin.concurrent.Volatile

internal open class DocumentEventDispatcherImpl private constructor(
  private val mySettings: DocumentSettings,
  private val myListeners: LockFreeCOWSortedArray<DocumentListener>,
  private val myPropertyListeners: DocumentPropertyChangeSupport,
  private val myReadOnlyListeners: MutableList<EditReadOnlyListener>,
  private val myFullUpdateListeners: MutableList<DocumentFullUpdateListener>,
): DocumentEventDispatcher {
  @Volatile protected var firingTextChange: Boolean = false
  @Volatile protected var bulkUpdateInProgress: Boolean = false
  @Volatile protected var bulkUpdateTrace: Throwable? = null
  @Volatile private var exceptions: DocumentDelayedExceptions? = null
  @Volatile private var listeners: Array<DocumentListener>? = null

  constructor(settings: DocumentSettings) : this(
    settings,
    LockFreeCOWSortedArray(PrioritizedDocumentListener.COMPARATOR, DocumentListener.ARRAY_FACTORY),
    DocumentPropertyChangeSupport(),
    ContainerUtil.createLockFreeCopyOnWriteList(),
    ContainerUtil.createLockFreeCopyOnWriteList(),
  )

  override fun addDocumentListener(listener: DocumentListener, parentDisposable: Disposable) {
    addDocumentListener(listener)
    Disposer.register(parentDisposable, DocumentListenerDisposable(myListeners, listener))
  }

  override fun addDocumentListener(listener: DocumentListener) {
    if (ArrayUtil.contains(listener, *getListeners())) {
      LOG.error("Already registered: $listener")
    }
    myListeners.add(listener)
  }

  override fun removeDocumentListener(listener: DocumentListener) {
    val success = myListeners.remove(listener)
    if (!success) {
      LOG.error(
        "Can't remove document listener (" + listener + "). " +
        "Registered listeners: " + getListeners().contentToString()
      )
    }
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    myPropertyListeners.addPropertyChangeListener(listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    myPropertyListeners.removePropertyChangeListener(listener)
  }

  override fun addEditReadOnlyListener(listener: EditReadOnlyListener) {
    myReadOnlyListeners.add(listener)
  }

  override fun removeEditReadOnlyListener(listener: EditReadOnlyListener) {
    myReadOnlyListeners.remove(listener)
  }

  override fun addFullUpdateListener(listener: DocumentFullUpdateListener) {
    myFullUpdateListeners.add(listener)
  }

  override fun removeFullUpdateListener(listener: DocumentFullUpdateListener) {
    myFullUpdateListeners.remove(listener)
  }

  override fun fireBeforeTextChange(event: DocumentEvent) {
    if (ShutDownTracker.isShutdownStarted()) {
      return
    }
    val listeners = getListeners()
    val exceptions = DocumentDelayedExceptions(isPCEWarningEnabled())
    ProgressManager.getInstance().executeNonCancelableSection {
      for (i in listeners.indices.reversed()) {
        try {
          listeners[i].beforeDocumentChange(event)
        } catch (e: Throwable) {
          exceptions.register(e)
        }
      }
    }
    this.listeners = listeners
    this.exceptions = exceptions
  }

  override fun fireTextChanged(event: DocumentEvent) {
    if (ShutDownTracker.isShutdownStarted()) {
      return
    }
    val listeners = this.listeners ?: throw IllegalStateException("beforeTextChanged should be called first")
    val exceptions = this.exceptions ?: throw IllegalStateException("beforeTextChanged should be called first")
    firingTextChange = true
    try {
      ProgressManager.getInstance().executeNonCancelableSection {
        for (listener in listeners) {
          try {
            listener.documentChanged(event)
          } catch (e: Throwable) {
            exceptions.register(e)
          }
        }
      }
    } finally {
      firingTextChange = false
      this.listeners = null
      this.exceptions = null
    }
    exceptions.rethrowPCE()
  }

  override fun firingTextChanged(): Boolean {
    return firingTextChange
  }

  override fun firePropertyChange(hostDocument: Document, isReadOnly: Boolean) {
    myPropertyListeners.firePropertyChange(hostDocument, Document.PROP_WRITABLE, !isReadOnly, isReadOnly)
  }

  override fun fireReadOnlyModificationAttempt(hostDocument: Document) {
    for (listener in myReadOnlyListeners) {
      listener.readOnlyModificationAttempt(hostDocument)
    }
  }

  override fun fireDocumentFullUpdated(hostDocument: Document) {
    for (listener in myFullUpdateListeners) {
      listener.onFullUpdateDocument(hostDocument)
    }
  }

  override fun fireBulkUpdateStarting(hostDocument: Document) {
    try {
      getBulkPublisher().updateStarted(hostDocument)
      val exceptions = DocumentDelayedExceptions(isPCEWarningEnabled())
      val listeners = getListeners()
      for (i in listeners.indices.reversed()) {
        try {
          listeners[i].bulkUpdateStarting(hostDocument)
        } catch (e: Throwable) {
          exceptions.register(e)
        }
      }
      exceptions.rethrowPCE()
    } finally {
      bulkUpdateInProgress = true
      bulkUpdateTrace = Throwable()
    }
  }

  override fun fireBulkUpdateFinished(hostDocument: Document) {
    bulkUpdateInProgress = false
    bulkUpdateTrace = null
    val exceptions = DocumentDelayedExceptions(isPCEWarningEnabled())
    val listeners = getListeners()
    for (listener in listeners) {
      try {
        listener.bulkUpdateFinished(hostDocument)
      } catch (e: Throwable) {
        exceptions.register(e)
      }
    }
    getBulkPublisher().updateFinished(hostDocument)
    exceptions.rethrowPCE()
  }

  override fun isInBulkUpdate(): Boolean {
    return bulkUpdateInProgress
  }

  override fun assertNotInBulkUpdate() {
    if (isInBulkUpdate()) {
      throw UnexpectedBulkUpdateStateException(bulkUpdateTrace)
    }
  }

  protected fun getListeners(): Array<DocumentListener> {
    return myListeners.getArray()
  }

  protected fun isPCEWarningEnabled(): Boolean {
    return mySettings.isPCEWarningEnabled()
  }

  companion object {
    private val LOG: Logger = logger<DocumentEventDispatcherImpl>()

    @Suppress("DEPRECATION")
    private fun getBulkPublisher(): com.intellij.openapi.editor.ex.DocumentBulkUpdateListener {
      return DocumentBulkUpdateListenerHolder.BULK_CHANGE_PUBLISHER
    }
  }
}
