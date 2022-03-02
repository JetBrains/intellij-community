// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.lang.Language
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import com.intellij.util.EventDispatcher

abstract class CommentTextFieldModelBase(
  final override val project: Project?,
  initialText: String,
  language: Language = getMarkdownLanguage() ?: PlainTextLanguage.INSTANCE,
) : CommentTextFieldModel {

  private val listeners = EventDispatcher.create(SimpleEventListener::class.java)

  final override val document: Document = LanguageTextField.createDocument(
    initialText,
    language,
    project,
    LanguageTextField.SimpleDocumentCreator()
  )

  override val content: CommentTextFieldModelContent = CommentTextFieldModelContentImpl(document as DocumentImpl)

  override var isBusy = false
    set(value) {
      field = value
      listeners.multicaster.eventOccurred()
    }

  override var error: Throwable? = null
    set(value) {
      field = value
      listeners.multicaster.eventOccurred()
    }

  override fun addStateListener(listener: SimpleEventListener) {
    listeners.addListener(listener)
  }
}

private class CommentTextFieldModelContentImpl(private val document: DocumentImpl) : CommentTextFieldModelContent {
  override var text: String
    get() = document.text
    set(value) {
      runWriteAction {
        document.setText(value)
      }
    }

  override var isReadOnly: Boolean
    get() = !document.isWritable
    set(value) {
      document.setReadOnly(value)
    }

  override var isAcceptSlashR: Boolean
    get() = document.acceptsSlashR()
    set(value) {
      document.setAcceptSlashR(value)
    }

  override fun clear() {
    runUndoTransparentWriteAction {
      document.setText("")
    }
  }
}

private fun getMarkdownLanguage(): Language? {
  val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("md") as? LanguageFileType
  return fileType?.language
}