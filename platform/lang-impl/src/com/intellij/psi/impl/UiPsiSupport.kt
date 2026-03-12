// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.uiDocument.UiDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.openapi.vfs.VirtualFile

class UiPsiSupport(
  private val project: Project,
) {
  fun isUiDocument(document: Document): Boolean {
    return getTopLevelUiDocument(document) != null
  }

  fun getPsiFile(document: Document): PsiFile? {
    val uiDocument = getTopLevelUiDocument(document)
    if (uiDocument == null) {
      return null
    }
    val state = uiDocument.getUserData(UI_PSI_STATE_KEY)
    if (state == null) {
      return null
    }
    val psiFile = state.psiFile
    if (psiFile.isValid) {
      return psiFile
    }
    clearUiPsiState(uiDocument)
    return null
  }

  fun getDocument(psiFile: PsiFile): Document? {
    return psiFile.getUserData(UI_PSI_DOCUMENT_KEY)
  }

  fun recordRealPsiFile(document: Document, psiFile: PsiFile?) {
    val uiDocument = UiDocumentManager.getInstance().getUiDocument(document)
    if (uiDocument == null) {
      return
    }
    if (psiFile == null) {
      clearUiPsiData(uiDocument)
      return
    }
    val template = createUiPsiTemplate(psiFile)
    if (template == null) {
      clearUiPsiData(uiDocument)
      return
    }
    uiDocument.putUserData(UI_PSI_TEMPLATE_KEY, template)
    if (!areDocumentsSynced(uiDocument, document)) {
      return
    }
    val state = uiDocument.getUserData(UI_PSI_STATE_KEY)
    val modificationStamp = uiDocument.modificationStamp
    if (state != null && state.modificationStamp == modificationStamp && state.psiFile.isValid) {
      return
    }
    cacheCommittedUiPsi(uiDocument, template, modificationStamp)
  }

  fun commitDocument(document: Document) {
    val uiDocument = getTopLevelUiDocument(document)
    if (uiDocument == null) {
      return
    }
    val template = uiDocument.getUserData(UI_PSI_TEMPLATE_KEY)
    if (template == null) {
      clearUiPsiState(uiDocument)
      return
    }
    val modificationStamp = uiDocument.modificationStamp
    cacheCommittedUiPsi(uiDocument, template, modificationStamp)
  }

  fun getLastCommittedDocument(document: Document): DocumentEx {
    val uiDocument = getTopLevelUiDocument(document)
    if (uiDocument == null) {
      throw IllegalArgumentException("Not a UI document: $document")
    }
    val lastCommittedDocument = uiDocument.getUserData(UI_PSI_STATE_KEY)?.lastCommittedDocument
    if (lastCommittedDocument != null) {
      return lastCommittedDocument
    }
    val uiDocumentImpl = uiDocument as DocumentImpl
    return uiDocumentImpl.freeze()
  }

  fun isCommitted(document: Document): Boolean {
    val uiDocument = getTopLevelUiDocument(document)
    if (uiDocument == null) {
      return false
    }
    val state = uiDocument.getUserData(UI_PSI_STATE_KEY)
    if (state == null) {
      return false
    }
    return state.modificationStamp == uiDocument.modificationStamp
  }

  fun doPostponedOperationsAndUnblockDocument(document: Document) {
    commitDocument(document)
  }

  private fun getTopLevelUiDocument(document: Document): Document? {
    val manager = UiDocumentManager.getInstance()
    val topLevelDocument = PsiDocumentManagerBase.getTopLevelDocument(document)
    if (!manager.isUiDocument(topLevelDocument)) {
      return null
    }
    return topLevelDocument
  }

  private fun areDocumentsSynced(uiDocument: Document, realDocument: Document): Boolean {
    return uiDocument.modificationStamp == realDocument.modificationStamp
  }

  private fun createUiPsiTemplate(psiFile: PsiFile): UiPsiTemplate? {
    if (!supportsUiPsi(psiFile)) {
      return null
    }
    return UiPsiTemplate(
      fileName = psiFile.name,
      language = psiFile.language,
      originalVirtualFile = psiFile.viewProvider.virtualFile,
    )
  }

  private fun cacheCommittedUiPsi(uiDocument: Document, template: UiPsiTemplate, modificationStamp: Long) {
    val uiPsiFile = createUiPsiFile(uiDocument, template)
    uiPsiFile.putUserData(UI_PSI_DOCUMENT_KEY, uiDocument)
    cacheUiPsiState(uiDocument, modificationStamp, uiPsiFile)
  }

  private fun createUiPsiFile(uiDocument: Document, template: UiPsiTemplate): PsiFile {
    return PsiFileFactory.getInstance(project).createFileFromText(
      template.fileName,
      template.language,
      uiDocument.immutableCharSequence,
      false,
      true,
      true,
      template.originalVirtualFile,
    )
  }

  private fun cacheUiPsiState(uiDocument: Document, modificationStamp: Long, uiPsiFile: PsiFile) {
    val uiDocumentImpl = uiDocument as DocumentImpl
    val lastCommittedDocument = uiDocumentImpl.freeze()
    val uiPsiState = UiPsiState(
      modificationStamp = modificationStamp,
      psiFile = uiPsiFile,
      lastCommittedDocument = lastCommittedDocument,
    )
    uiDocument.putUserData(UI_PSI_STATE_KEY, uiPsiState)
  }

  private fun clearUiPsiData(uiDocument: Document) {
    clearUiPsiState(uiDocument)
    uiDocument.putUserData(UI_PSI_TEMPLATE_KEY, null)
  }

  private fun clearUiPsiState(uiDocument: Document) {
    uiDocument.putUserData(UI_PSI_STATE_KEY, null)
  }

  private fun supportsUiPsi(originalPsiFile: PsiFile): Boolean {
    // Phase 1 only supports single-language copies; mixed-language view providers need a dedicated UI-side view provider.
    return originalPsiFile.viewProvider.languages.size == 1
  }

  private data class UiPsiTemplate(
    val fileName: String,
    val language: com.intellij.lang.Language,
    val originalVirtualFile: VirtualFile?,
  )

  private data class UiPsiState(
    val modificationStamp: Long,
    val psiFile: PsiFile,
    val lastCommittedDocument: DocumentEx,
  )

  companion object {
    private val UI_PSI_TEMPLATE_KEY = Key.create<UiPsiTemplate>("UI_PSI_TEMPLATE_KEY")
    private val UI_PSI_STATE_KEY = Key.create<UiPsiState>("UI_PSI_STATE_KEY")
    private val UI_PSI_DOCUMENT_KEY = Key.create<Document>("UI_PSI_DOCUMENT_KEY")
  }
}
