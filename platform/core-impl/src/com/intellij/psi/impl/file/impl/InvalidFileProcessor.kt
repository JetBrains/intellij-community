// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.isContextRelevant
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.impl.DebugUtil
import com.intellij.util.ThrowableRunnable

internal class InvalidFileProcessor(
  private val fileManager: FileManagerImpl,
  private val project: Project,
  private val vFileToViewProviderMap: FileViewProviderCache,
  private val isMove: Boolean,
) {

  fun processInvalidFilesAfterVfsMoveOrDelete() {
    val originalFileToPsiFileMap = vFileToViewProviderMap.getAllEntries()
    val fileToPsiFileMap = originalFileToPsiFileMap.toMutableList()
    if (isMove) {
      vFileToViewProviderMap.clear()
    }

    val report = dropInvalidFilesAndPrepareIrrelevantViewProviderReport(fileToPsiFileMap)
    val irrelevantEntriesConvertedToRelevant = prepareRelevantProvidersForFilesWithIrrelevantProviders(report)

    irrelevantEntriesConvertedToRelevant.forEach { updatedEntry ->
      fileToPsiFileMap.add(updatedEntry.newEntry)
    }

    vFileToViewProviderMap.replaceAll(fileToPsiFileMap)
    markInvalidations(originalFileToPsiFileMap)
  }

  /**
   * removes invalid entries from fileToPsiFileMap and prepares IrrelevantViewProviderReport
   */
  private fun dropInvalidFilesAndPrepareIrrelevantViewProviderReport(fileToPsiFileMap: MutableList<FileViewProviderCache.Entry>): IrrelevantViewProviderReport {
    val filesHavingRelevantViewProviders = HashSet<VirtualFile>()
    val irrelevantViewProviders = mutableMapOf<VirtualFile, FileViewProviderCache.Entry>()

    val iterator = fileToPsiFileMap.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()

      val vFile = entry.file
      val context = entry.context
      val viewProvider = entry.provider

      if (!vFile.isValid()) {
        iterator.remove()
        continue
      }

      if (isMove) {
        val psiFile1 = fileManager.findFile(vFile, context)
        if (psiFile1 == null) {
          iterator.remove()
          continue
        }

        if (!FileManagerImpl.areViewProvidersEquivalent(viewProvider, psiFile1.getViewProvider())) {
          iterator.remove()
          continue
        }

        FileManagerImpl.clearPsiCaches(viewProvider)

        if (context !== anyContext()) {
          if (isContextRelevant(vFile, context, project)) {
            filesHavingRelevantViewProviders.add(vFile)
          }
          else {
            irrelevantViewProviders.putIfAbsent(vFile, entry)
            iterator.remove()
          }
        }
      }
      else {
        if (!fileManager.evaluateValidity(viewProvider as AbstractFileViewProvider)) {
          iterator.remove()
        }
      }
    }

    return IrrelevantViewProviderReport(filesHavingRelevantViewProviders, irrelevantViewProviders)
  }

  private fun prepareRelevantProvidersForFilesWithIrrelevantProviders(result: IrrelevantViewProviderReport): Sequence<UpdatedEntry> {
    if (result.irrelevantViewProviders.isEmpty()) {
      return emptySequence()
    }

    val codeInsightContextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)

    val filesWithIrrelevantProvidersOnly = result.irrelevantViewProviders.values.asSequence().filter { entry ->
      val hasRelevantContexts = result.filesHavingRelevantViewProviders.contains(entry.file)
      if (hasRelevantContexts) {
        LOG.debug {
          val viewProvider = entry.provider
          val context = codeInsightContextManager.getCodeInsightContextRaw(viewProvider)
          "- View provider $viewProvider with irrelevant context $context has not survived moving"
        }
      }
      !hasRelevantContexts
    }

    val relevantEntries = filesWithIrrelevantProvidersOnly.map { entry ->
      val vFile = entry.file
      val viewProvider = entry.provider
      codeInsightContextManager.setCodeInsightContext(viewProvider, anyContext())

      LOG.debug {
        val context = codeInsightContextManager.getCodeInsightContextRaw(viewProvider)
        "+ View provider $viewProvider with irrelevant context $context has survived moving"
      }
      UpdatedEntry(FileViewProviderCache.Entry(vFile, anyContext(), viewProvider), entry)
    }

    return relevantEntries
  }

  private fun markInvalidations(originalFileToPsiFileMap: List<FileViewProviderCache.Entry>) {
    if (originalFileToPsiFileMap.isEmpty()) return

    DebugUtil.performPsiModification(null, ThrowableRunnable {
      for (entry in originalFileToPsiFileMap) {
        val viewProvider = entry.provider
        if (vFileToViewProviderMap.get(entry.file, entry.context) !== viewProvider) {
          fileManager.markInvalidated(viewProvider)
        }
      }
    })
  }
}

private val LOG = logger<InvalidFileProcessor>()
