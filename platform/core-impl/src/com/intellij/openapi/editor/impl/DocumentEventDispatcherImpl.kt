// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEventDispatcher
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.EditReadOnlyListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.util.Disposer
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import java.beans.PropertyChangeListener

internal open class DocumentEventDispatcherImpl private constructor(
  settings: DocumentSettings,
  protected val listeners: LockFreeCOWSortedArray<DocumentListener>,
  private val propertyListeners: DocumentPropertyChangeSupport,
  private val readOnlyListeners: MutableList<EditReadOnlyListener>,
): DocumentEventDispatcher {
  protected val textUpdate: DocumentTextUpdate = DocumentTextUpdate.Real(settings, listeners)
  protected val bulkUpdate: DocumentBulkUpdate = DocumentBulkUpdate.Real(settings, listeners)

  constructor(settings: DocumentSettings) : this(
    settings,
    LockFreeCOWSortedArray(PrioritizedDocumentListener.COMPARATOR, DocumentListener.ARRAY_FACTORY),
    DocumentPropertyChangeSupport(),
    ContainerUtil.createLockFreeCopyOnWriteList(),
  )

  init {
    addDeprecatedBulkUpdatePublisher()
  }

  fun <T> withFiringTextUpdate(changeEvent: DocumentEvent, action: () -> T): T {
    return textUpdate.withFiringTextUpdate(changeEvent, null, action)
  }

  override fun addDocumentListener(listener: DocumentListener, parentDisposable: Disposable) {
    addDocumentListener(listener)
    Disposer.register(parentDisposable, DocumentListenerDisposable(listeners, listener))
  }

  override fun addDocumentListener(listener: DocumentListener) {
    if (ArrayUtil.contains(listener, *getListeners())) {
      LOG.error("Already registered: $listener")
    }
    listeners.add(listener)
  }

  override fun removeDocumentListener(listener: DocumentListener) {
    val success = listeners.remove(listener)
    if (!success) {
      LOG.error(
        "Can't remove document listener (" + listener + "). " +
        "Registered listeners: " + getListeners().contentToString()
      )
    }
  }

  override fun firingTextChanged(): Boolean {
    return textUpdate.isInTextUpdate()
  }

  override fun setBulkModeStatus(hostDocument: Document, status: Boolean) {
    bulkUpdate.setBulkUpdateStatus(hostDocument, status)
  }

  override fun isInBulkUpdate(): Boolean {
    return bulkUpdate.isInBulkUpdate()
  }

  override fun assertNotInBulkUpdate() {
    bulkUpdate.assertNotInBulkUpdate()
  }

  final override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    propertyListeners.addPropertyChangeListener(listener)
  }

  final override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    propertyListeners.removePropertyChangeListener(listener)
  }

  final override fun addEditReadOnlyListener(listener: EditReadOnlyListener) {
    readOnlyListeners.add(listener)
  }

  final override fun removeEditReadOnlyListener(listener: EditReadOnlyListener) {
    readOnlyListeners.remove(listener)
  }

  final override fun firePropertyChange(hostDocument: Document, isReadOnly: Boolean) {
    propertyListeners.firePropertyChange(
      hostDocument,
      Document.PROP_WRITABLE,
      !isReadOnly,
      isReadOnly,
    )
  }

  final override fun fireReadOnlyModificationAttempt(hostDocument: Document) {
    for (listener in readOnlyListeners) {
      listener.readOnlyModificationAttempt(hostDocument)
    }
  }

  protected fun getListeners(): Array<DocumentListener> {
    return listeners.getArray()
  }

  private fun addDeprecatedBulkUpdatePublisher() {
    // it is scheduled for removal api, don't care about document listener priority
    listeners.add(object : DocumentListener {
      override fun bulkUpdateStarting(document: Document) {
        getBulkPublisher().updateStarted(document)
      }
      override fun bulkUpdateFinished(document: Document) {
        getBulkPublisher().updateFinished(document)
      }
    })
  }

  companion object {
    private val LOG: Logger = logger<DocumentEventDispatcherImpl>()

    @Suppress("DEPRECATION")
    private fun getBulkPublisher(): com.intellij.openapi.editor.ex.DocumentBulkUpdateListener {
      return DocumentBulkUpdateListenerHolder.BULK_CHANGE_PUBLISHER
    }
  }
}
