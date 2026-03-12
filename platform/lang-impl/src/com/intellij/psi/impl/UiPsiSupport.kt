// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.EditorLockFreeTyping
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.uiDocument.UiDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.LanguageFileViewProviders
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.testFramework.ReadOnlyLightVirtualFile
import com.intellij.util.ui.EDT

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
    val state = getStoredUiPsiState(uiDocument)
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

  @Suppress("UNUSED_PARAMETER")
  fun getPsiFile(document: Document, context: CodeInsightContext): PsiFile? {
    return getPsiFile(document)
  }

  fun getDocument(psiFile: PsiFile): Document? {
    return psiFile.getUserData(UI_PSI_DOCUMENT_KEY)
  }

  fun recordRealPsiFile(realDocument: Document, realPsiFile: PsiFile?) {
    if (!EditorLockFreeTyping.isEnabled()) {
      return
    }
    val uiDocument = UiDocumentManager.getInstance().getUiDocument(realDocument)
    if (uiDocument == null) {
      return
    }
    if (realPsiFile == null) {
      clearUiPsiData(uiDocument)
      return
    }
    val template = createUiPsiTemplate(realPsiFile)
    if (template == null) {
      clearUiPsiData(uiDocument)
      return
    }
    cacheUiPsiTemplate(uiDocument, template)
    if (!areDocumentsSynced(uiDocument, realDocument)) {
      return
    }
    val state = getStoredUiPsiState(uiDocument)
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
    val template = getStoredUiPsiTemplate(uiDocument)
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
    val state = getStoredUiPsiState(uiDocument)
    if (state != null) {
      return state.lastCommittedDocument
    }
    val uiDocumentImpl = uiDocument as DocumentImpl
    return uiDocumentImpl.freeze()
  }

  fun isCommitted(document: Document): Boolean {
    val uiDocument = getTopLevelUiDocument(document)
    if (uiDocument == null) {
      return false
    }
    val state = getStoredUiPsiState(uiDocument)
    if (state == null) {
      return false
    }
    return state.modificationStamp == uiDocument.modificationStamp
  }

  fun doPostponedOperationsAndUnblockDocument(document: Document) {
    commitDocument(document)
  }

  private fun getTopLevelUiDocument(document: Document): Document? {
    val topLevelDocument = PsiDocumentManagerBase.getTopLevelDocument(document)
    val uiDocumentManager = UiDocumentManager.getInstance()
    if (uiDocumentManager.isUiDocument(topLevelDocument)) {
      return topLevelDocument
    }
    if (EDT.isCurrentThreadEdt()) {
      if (topLevelDocument.getUserData(EditorLockFreeTyping.USE_UI_PSI_FOR_DOCUMENT_KEY) == true) {
        return uiDocumentManager.getUiDocument(topLevelDocument)
      }
    }
    return null
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
      originalPsiFile = psiFile,
    )
  }

  private fun cacheCommittedUiPsi(
    uiDocument: Document,
    template: UiPsiTemplate,
    modificationStamp: Long,
  ) {
    val uiPsiFile = createUiPsiFile(uiDocument, template)
    if (uiPsiFile == null) {
      clearUiPsiState(uiDocument)
      return
    }
    uiPsiFile.putUserData(UI_PSI_DOCUMENT_KEY, uiDocument)
    cacheUiPsiState(uiDocument, modificationStamp, uiPsiFile)
  }

  private fun createUiPsiFile(
    uiDocument: Document,
    template: UiPsiTemplate,
  ): PsiFile? {
    val psiManager = PsiManager.getInstance(project)
    val virtualFile = createUiPsiVirtualFile(uiDocument, template)
    val viewProvider = createUiPsiViewProvider(psiManager, virtualFile, template)
    val baseLanguage = viewProvider.baseLanguage
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(baseLanguage)
    if (parserDefinition == null) {
      return null
    }
    val uiPsiFile = viewProvider.getPsi(baseLanguage)
    if (uiPsiFile == null) {
      return null
    }
    PsiFileFactoryImpl.markGenerated(uiPsiFile)
    uiPsiFile.putUserData(PsiFileFactory.ORIGINAL_FILE, template.originalPsiFile)
    return uiPsiFile
  }

  private fun createUiPsiViewProvider(
    psiManager: PsiManager,
    virtualFile: ReadOnlyLightVirtualFile,
    template: UiPsiTemplate,
  ): FileViewProvider {
    val providerFactory = LanguageFileViewProviders.INSTANCE.forLanguage(template.language)
    val viewProvider: FileViewProvider
    if (providerFactory != null) {
      viewProvider = providerFactory.createFileViewProvider(virtualFile, template.language, psiManager, false)
    }
    else {
      viewProvider = SingleRootFileViewProvider(psiManager, virtualFile, false)
    }
    return viewProvider
  }

  private fun createUiPsiVirtualFile(
    uiDocument: Document,
    template: UiPsiTemplate,
  ): ReadOnlyLightVirtualFile {
    val virtualFile = object : ReadOnlyLightVirtualFile(
      template.fileName,
      template.language,
      uiDocument.immutableCharSequence,
    ) {
      init {
        modificationStamp = uiDocument.modificationStamp
      }
    }
    val originalVirtualFile = template.originalVirtualFile
    if (originalVirtualFile != null) {
      virtualFile.originalFile = originalVirtualFile
      virtualFile.fileType = originalVirtualFile.fileType
    }
    return virtualFile
  }

  private fun cacheUiPsiState(
    uiDocument: Document,
    modificationStamp: Long,
    uiPsiFile: PsiFile,
  ) {
    val uiDocumentImpl = uiDocument as DocumentImpl
    val lastCommittedDocument = uiDocumentImpl.freeze()
    val uiPsiState = UiPsiState(
      modificationStamp = modificationStamp,
      psiFile = uiPsiFile,
      lastCommittedDocument = lastCommittedDocument,
    )
    uiDocument.putUserData(UI_PSI_STATE_KEY, uiPsiState)
  }

  private fun cacheUiPsiTemplate(
    uiDocument: Document,
    template: UiPsiTemplate,
  ) {
    uiDocument.putUserData(UI_PSI_TEMPLATE_KEY, template)
  }

  private fun getStoredUiPsiTemplate(uiDocument: Document): UiPsiTemplate? {
    return uiDocument.getUserData(UI_PSI_TEMPLATE_KEY)
  }

  private fun getStoredUiPsiState(uiDocument: Document): UiPsiState? {
    return uiDocument.getUserData(UI_PSI_STATE_KEY)
  }

  private fun clearUiPsiData(uiDocument: Document) {
    clearUiPsiState(uiDocument)
    uiDocument.putUserData(UI_PSI_TEMPLATE_KEY, null)
  }

  private fun clearUiPsiState(uiDocument: Document) {
    uiDocument.putUserData(UI_PSI_STATE_KEY, null)
  }

  private fun supportsUiPsi(originalPsiFile: PsiFile): Boolean {
    if (originalPsiFile.viewProvider.languages.size != 1) {
      return false
    }
    return LanguageParserDefinitions.INSTANCE.forLanguage(originalPsiFile.language) != null
  }

  private data class UiPsiTemplate(
    val fileName: String,
    val language: Language,
    val originalVirtualFile: VirtualFile?,
    val originalPsiFile: PsiFile,
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
