// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.uiDocument

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.util.DocumentEventUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.TransferredWriteActionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls


internal class UiDocumentSync(
  private val coroutineScope: CoroutineScope,
) : PrioritizedDocumentListener {

  private val uiToRealSync = UiToRealSync()
  private val realToUiSync = RealToUiSync()

  override fun getPriority(): Int {
    return Int.MIN_VALUE + 1
  }

  override fun documentChanged(event: DocumentEvent) {
    ThreadingAssertions.assertEventDispatchThread()
    val document = event.document
    if (isUiDocument(document)) {
      if (!realToUiSync.isInProgress(document.modificationStamp)) {
        uiToRealSync.documentChanged(event)
      }
    } else if (isRealDocument(document)) {
      if (!uiToRealSync.isInProgress(document.modificationStamp)) {
        realToUiSync.documentChanged(event)
      }
    } else {
      error("Unknown document ${document}")
    }
  }

  private fun isUiDocument(document: Document): Boolean {
    return getRealDocument(document) != null
  }

  private fun isRealDocument(document: Document): Boolean {
    return getUiDocument(document) != null
  }

  private fun getUiDocument(document: Document): DocumentImpl? {
    val manager = UiDocumentManager.getInstance()
    return manager.getUiDocument(document)
  }

  private fun getRealDocument(document: Document): DocumentImpl? {
    val manager = UiDocumentManager.getInstance()
    return manager.getRealDocument(document)
  }

  private fun isMoveEvent(event: DocumentEvent): Boolean {
    return DocumentEventUtil.isMoveInsertion(event) ||
           DocumentEventUtil.isMoveDeletion(event)
  }

  private fun assertDocumentAreSynced(sourceDocument: DocumentEvent, targetDocument: DocumentImpl) {
    if (sourceDocument.offset + sourceDocument.oldLength > targetDocument.textLength) {
      error(
        "Text length mismatch: event expects length ${sourceDocument.offset + sourceDocument.oldLength} " +
        "but UI document has ${targetDocument.textLength}"
      )
    }
  }

  private fun isWriteAccess(): Boolean {
    return ApplicationManager.getApplication().isWriteAccessAllowed()
  }

  private inner class UiToRealSync : Sync() {
    override fun documentChanged(event: DocumentEvent) {
      val uiDocument = event.document as DocumentImpl
      val realDocument = getRealDocument(uiDocument)!!
      if (isWriteAccess()) {
        changeDocument(
          sourceEvent = event,
          sourceDocument = uiDocument,
          targetDocument = realDocument,
        )
      } else {
        changeDocumentAsync(
          sourceEvent = event,
          sourceDocument = uiDocument,
          targetDocument = realDocument,
          commandProject = CommandProcessor.getInstance().currentCommandProject,
          commandGroupId = CommandProcessor.getInstance().currentCommandGroupId,
          commandName = CommandProcessor.getInstance().currentCommandName,
        )
      }
    }
  }

  private inner class RealToUiSync : Sync() {
    override fun documentChanged(event: DocumentEvent) {
      val realDocument = event.document as DocumentImpl
      val uiDocument = getUiDocument(realDocument)!!
      assertDocumentAreSynced(event, uiDocument)
      changeDocument(
        sourceEvent = event,
        sourceDocument = realDocument,
        targetDocument = uiDocument,
      )
    }
  }

  private abstract inner class Sync : DocumentListener {
    protected val inProgress: MutableSet<Long> = mutableSetOf()

    fun isInProgress(modStamp: Long): Boolean {
      return inProgress.contains(modStamp)
    }

    protected fun changeDocument(
      sourceEvent: DocumentEvent,
      sourceDocument: DocumentImpl,
      targetDocument: DocumentImpl,
    ) {
      val sourceModStamp = sourceDocument.modificationStamp
      startModification(sourceModStamp)
      try {
        applyDocumentChange(sourceEvent, targetDocument, sourceModStamp)
      } finally {
        finishModification(sourceModStamp)
      }
    }

    protected fun changeDocumentAsync(
      sourceEvent: DocumentEvent,
      sourceDocument: DocumentImpl,
      targetDocument: DocumentImpl,
      commandProject: Project?,
      commandGroupId: Any?,
      commandName: @Nls String?,
    ) {
      val sourceModStamp = sourceDocument.modificationStamp
      startModification(sourceModStamp)
      val documentChangeCommand = Runnable {
        try {
          applyDocumentChange(sourceEvent, targetDocument, sourceModStamp)
        } finally {
          finishModification(sourceModStamp)
        }
      }
      invokeLaterWithWriteAction {
        UiDocumentManager.getInstance().executeCommand(
          commandProject,
          commandName,
          commandGroupId,
          documentChangeCommand,
        )
      }
    }

    private fun finishModification(sourceModStamp: Long) {
      inProgress.remove(sourceModStamp)
    }

    private fun startModification(sourceModStamp: Long) {
      val added = inProgress.add(sourceModStamp)
      if (!added) {
        error("Modification stamp $sourceModStamp is already in progress")
      }
    }

    private fun applyDocumentChange(
      sourceEvent: DocumentEvent,
      targetDocument: DocumentImpl,
      sourceModStamp: Long,
    ) {
      val moveOffset = if (isMoveEvent(sourceEvent)) {
        sourceEvent.moveOffset
      } else {
        sourceEvent.offset
      }
      targetDocument.replaceString(
        sourceEvent.offset,
        sourceEvent.offset + sourceEvent.oldLength,
        moveOffset,
        sourceEvent.newFragment,
        sourceModStamp,
        false,
      )
    }

    private fun invokeLaterWithWriteAction(action: () -> Unit) {
      coroutineScope.launch(DISPATCHER) {
        backgroundWriteAction {
          val application = ApplicationManager.getApplication()
          application.service<TransferredWriteActionService>().runOnEdtWithTransferredWriteActionAndWait {
            action()
          }
        }
      }
    }
  }

  companion object {
    internal val DISPATCHER by lazy {
      AppExecutorUtil.createBoundedApplicationPoolExecutor("UI_DOC_DISPATCHER", 1).asCoroutineDispatcher()
    }
  }
}
