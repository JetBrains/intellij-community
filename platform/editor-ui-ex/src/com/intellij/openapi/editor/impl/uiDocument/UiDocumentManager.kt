// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.uiDocument

import com.intellij.openapi.application.isEditorLockFreeTypingEnabled
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean


@Internal
@Service(Level.APP)
class UiDocumentManager(private val coroutineScope: CoroutineScope) {

  private val isCommandInProgress = AtomicBoolean()

  fun bindUiDocument(realDocument: Document) {
    if (isLockFreeEnabled() && isLockFreeAllowed(realDocument)) {
      val existingUiDocument = getUiDocument(realDocument)
      if (existingUiDocument != null) {
        error("UI document already bound $existingUiDocument")
      }
      val uiDocument = createLockFreeDocument(realDocument as DocumentImpl)
      val sync = UiDocumentSync(coroutineScope)
      uiDocument.addDocumentListener(sync)
      realDocument.addDocumentListener(sync)
      uiDocument.putUserData(REAL_DOCUMENT_KEY, realDocument)
      realDocument.putUserData(UI_DOCUMENT_KEY, uiDocument)
    }
  }

  fun isUiDocumentChangeInProgress(): Boolean {
    return isCommandInProgress.get()
  }

  fun getUiDocument(realDocument: Document): DocumentImpl? {
    return realDocument.getUserData(UI_DOCUMENT_KEY)
  }

  internal fun getRealDocument(uiDocument: Document): DocumentImpl? {
    return uiDocument.getUserData(REAL_DOCUMENT_KEY)
  }

  internal fun executeCommand(
    commandProject: Project?,
    commandName: @Nls String?,
    commandGroupId: Any?,
    command: Runnable,
  ) {
    val oldVal = isCommandInProgress.get()
    isCommandInProgress.set(true)
    try {
      CommandProcessor.getInstance().executeCommand(
        commandProject,
        command,
        commandName,
        commandGroupId,
      )
    } finally {
      isCommandInProgress.set(oldVal)
    }
  }

  private fun createLockFreeDocument(document: DocumentImpl): DocumentImpl {
    val acceptsSlashR = document.acceptsSlashR()
    val chars = document.immutableCharSequence
    val doc = DocumentImpl(chars, acceptsSlashR, true)
    doc.modificationStamp = document.modificationStamp
    return doc
  }

  private fun isLockFreeAllowed(document: Document): Boolean {
    return document is DocumentImpl && document.isWriteThreadOnly
  }

  private fun isLockFreeEnabled(): Boolean {
    return isEditorLockFreeTypingEnabled
  }

  companion object {
    @JvmStatic
    fun getInstance(): UiDocumentManager = service()

    private val UI_DOCUMENT_KEY: Key<DocumentImpl> = Key.create("UI_DOCUMENT_KEY")
    private val REAL_DOCUMENT_KEY: Key<DocumentImpl> = Key.create("REAL_DOCUMENT_KEY")
  }
}
