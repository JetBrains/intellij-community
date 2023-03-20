// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.INTERNAL
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.whenOperationFinished
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.psi.ExternalChangeAction
import com.intellij.util.EventDispatcher

class DocumentsChangesProvider(private val isIgnoreExternalChanges: Boolean) : FilesChangesProvider, DocumentListener {
  private val eventDispatcher = EventDispatcher.create(FilesChangesListener::class.java)

  override fun subscribe(listener: FilesChangesListener, parentDisposable: Disposable) {
    eventDispatcher.addListener(listener, parentDisposable)
  }

  private val bulkUpdateOperation = AtomicOperationTrace(name = "Bulk document update operation")

  private fun isExternalModification() =
    ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction::class.java)

  override fun documentChanged(event: DocumentEvent) {
    if (isIgnoreExternalChanges && isExternalModification()) return
    val document = event.document
    val fileDocumentManager = FileDocumentManager.getInstance()
    val file = fileDocumentManager.getFile(document) ?: return
    when (bulkUpdateOperation.isOperationInProgress()) {
      true -> {
        eventDispatcher.multicaster.onFileChange(file.path, document.modificationStamp, INTERNAL)
      }
      else -> {
        eventDispatcher.multicaster.init()
        eventDispatcher.multicaster.onFileChange(file.path, document.modificationStamp, INTERNAL)
        eventDispatcher.multicaster.apply()
      }
    }
  }

  override fun bulkUpdateStarting(document: Document) {
    bulkUpdateOperation.traceStart()
  }

  override fun bulkUpdateFinished(document: Document) {
    bulkUpdateOperation.traceFinish()
  }

  init {
    bulkUpdateOperation.whenOperationStarted { eventDispatcher.multicaster.init() }
    bulkUpdateOperation.whenOperationFinished { eventDispatcher.multicaster.apply() }
  }
}