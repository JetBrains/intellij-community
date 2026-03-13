// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

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


internal class UiPsiSupport(private val project: Project) {

  fun isUiDocument(uiOrRealDocument: Document): Boolean {
    return getTopLevelUiDocument(uiOrRealDocument) != null
  }

  fun getPsiFile(uiOrRealDocument: Document): PsiFile? {
    val uiDocument = getTopLevelUiDocument(uiOrRealDocument)
    if (uiDocument == null) {
      return null
    }
    val state = getStoredUiPsiState(uiDocument)
    if (state == null) {
      return null
    }
    if (state.uiPsiFile.isValid) {
      return state.uiPsiFile
    }
    clearUiPsiState(uiDocument)
    return null
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
    val currentUiDocModStamp = uiDocument.modificationStamp
    if (state?.isUpToDateForStamp(currentUiDocModStamp) == true) {
      return
    }
    cacheCommittedUiPsi(uiDocument, template, currentUiDocModStamp)
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
      return state.frozenUiDocument
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
    return state.uiDocModStamp == uiDocument.modificationStamp
  }

  fun doPostponedOperationsAndUnblockDocument(document: Document) {
    commitDocument(document)
  }

  private fun getTopLevelUiDocument(uiOrRealDocument: Document): Document? {
    val topLevelDocument = PsiDocumentManagerBase.getTopLevelDocument(uiOrRealDocument)
    val uiDocumentManager = UiDocumentManager.getInstance()
    if (uiDocumentManager.isUiDocument(topLevelDocument)) {
      return topLevelDocument
    }
    if (EditorLockFreeTyping.isInUiPsiScope(topLevelDocument)) {
      return uiDocumentManager.getUiDocument(topLevelDocument)
    }
    return null
  }

  private fun areDocumentsSynced(uiDocument: Document, realDocument: Document): Boolean {
    return uiDocument.modificationStamp == realDocument.modificationStamp
  }

  private fun createUiPsiTemplate(realPsiFile: PsiFile): UiPsiTemplate? {
    if (!supportsUiPsi(realPsiFile)) {
      return null
    }
    return UiPsiTemplate(
      fileName = realPsiFile.name,
      language = realPsiFile.language,
      realVirtualFile = realPsiFile.viewProvider.virtualFile,
      realPsiFile = realPsiFile,
    )
  }

  private fun cacheCommittedUiPsi(
    uiDocument: Document,
    template: UiPsiTemplate,
    uiDocModStamp: Long,
  ) {
    val uiPsiFile = createUiPsiFile(uiDocument, template)
    if (uiPsiFile == null) {
      clearUiPsiState(uiDocument)
    } else {
      cacheUiPsiState(uiDocument, uiDocModStamp, uiPsiFile)
    }
  }

  private fun createUiPsiFile(
    uiDocument: Document,
    template: UiPsiTemplate,
  ): PsiFile? {
    val psiManager = PsiManager.getInstance(project)
    val uiVirtualFile = createUiPsiVirtualFile(uiDocument, template)
    val uiViewProvider = createUiPsiViewProvider(psiManager, uiVirtualFile, template)
    val baseLanguage = uiViewProvider.baseLanguage
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(baseLanguage)
    if (parserDefinition == null) {
      return null
    }
    val uiPsiFile = uiViewProvider.getPsi(baseLanguage)
    if (uiPsiFile == null) {
      return null
    }
    PsiFileFactoryImpl.markGenerated(uiPsiFile)
    uiPsiFile.putUserData(PsiFileFactory.ORIGINAL_FILE, template.realPsiFile)
    return uiPsiFile
  }

  private fun createUiPsiViewProvider(
    psiManager: PsiManager,
    uiVirtualFile: UiVirtualFile,
    template: UiPsiTemplate,
  ): FileViewProvider {
    val providerFactory = LanguageFileViewProviders.INSTANCE.forLanguage(template.language)
    if (providerFactory != null) {
      return providerFactory.createFileViewProvider(
        uiVirtualFile,
        template.language,
        psiManager,
        /* eventSystemEnabled = */ false,
      )
    }
    return SingleRootFileViewProvider(psiManager, uiVirtualFile, false)
  }

  private fun createUiPsiVirtualFile(
    uiDocument: Document,
    template: UiPsiTemplate,
  ): UiVirtualFile {
    val uiVirtualFile = UiVirtualFile(uiDocument, template)
    val originalVirtualFile = template.realVirtualFile
    if (originalVirtualFile != null) {
      uiVirtualFile.originalFile = originalVirtualFile
      uiVirtualFile.fileType = originalVirtualFile.fileType
    }
    return uiVirtualFile
  }

  private fun cacheUiPsiState(
    uiDocument: Document,
    uiDocumentModificationStamp: Long,
    uiPsiFile: PsiFile,
  ) {
    uiPsiFile.putUserData(UI_PSI_DOCUMENT_KEY, uiDocument)
    val lastCommittedDocument = (uiDocument as DocumentImpl).freeze()
    val uiPsiState = UiPsiState(
      uiDocModStamp = uiDocumentModificationStamp,
      uiPsiFile = uiPsiFile,
      frozenUiDocument = lastCommittedDocument,
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

  private fun supportsUiPsi(realPsiFile: PsiFile): Boolean {
    if (realPsiFile.viewProvider.languages.size != 1) {
      return false
    }
    return LanguageParserDefinitions.INSTANCE.forLanguage(realPsiFile.language) != null
  }

  private data class UiPsiTemplate(
    val fileName: String,
    val language: Language,
    val realVirtualFile: VirtualFile?,
    val realPsiFile: PsiFile,
  )

  private data class UiPsiState(
    val uiDocModStamp: Long,
    val uiPsiFile: PsiFile,
    val frozenUiDocument: DocumentEx,
  ) {
    fun isUpToDateForStamp(currentUiDocModStamp: Long): Boolean {
      return uiDocModStamp == currentUiDocModStamp && uiPsiFile.isValid
    }
  }

  private class UiVirtualFile(
    uiDocument: Document,
    template: UiPsiTemplate,
  ) : ReadOnlyLightVirtualFile(
    template.fileName,
    template.language,
    uiDocument.immutableCharSequence,
  ) {
    init {
      modificationStamp = uiDocument.modificationStamp
    }
  }

  companion object {
    private val UI_PSI_TEMPLATE_KEY = Key.create<UiPsiTemplate>("UI_PSI_TEMPLATE_KEY")
    private val UI_PSI_STATE_KEY = Key.create<UiPsiState>("UI_PSI_STATE_KEY")
    private val UI_PSI_DOCUMENT_KEY = Key.create<Document>("UI_PSI_DOCUMENT_KEY")
  }
}
