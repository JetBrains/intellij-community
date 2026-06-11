// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener

/**
 * This interface is responsible for storing document listeners and dispatching document-related notifications
 *
 * @see DocumentListener
 * @see DocumentFullUpdateListener
 * @see EditReadOnlyListener
 * @see Document.addPropertyChangeListener
 */
@ApiStatus.Internal
interface DocumentEventDispatcher {
  fun addDocumentListener(listener: DocumentListener, parentDisposable: Disposable)
  fun addDocumentListener(listener: DocumentListener)
  fun removeDocumentListener(listener: DocumentListener)
  fun fireBeforeTextChange(event: DocumentEvent)
  fun fireTextChanged(event: DocumentEvent)
  fun firingTextChanged(): Boolean
  fun fireBulkUpdateStarting(hostDocument: Document)
  fun fireBulkUpdateFinished(hostDocument: Document)
  fun isInBulkUpdate(): Boolean
  fun assertNotInBulkUpdate()

  fun addPropertyChangeListener(listener: PropertyChangeListener)
  fun removePropertyChangeListener(listener: PropertyChangeListener)
  fun firePropertyChange(hostDocument: Document, isReadOnly: Boolean)

  fun addEditReadOnlyListener(listener: EditReadOnlyListener)
  fun removeEditReadOnlyListener(listener: EditReadOnlyListener)
  fun fireReadOnlyModificationAttempt(hostDocument: Document)

  // TODO: FullUpdate api looks redundant
  fun addFullUpdateListener(listener: DocumentFullUpdateListener)
  fun removeFullUpdateListener(listener: DocumentFullUpdateListener)
  fun fireDocumentFullUpdated(hostDocument: Document)
}
