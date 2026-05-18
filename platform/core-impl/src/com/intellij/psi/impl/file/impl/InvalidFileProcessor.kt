// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.psi.impl.DebugUtil

internal class InvalidFileProcessor(
  private val fileManager: FileManagerImpl,
  private val vFileToViewProviderMap: FileViewProviderCache,
) {

  fun processInvalidFilesAfterVfsMoveOrDelete() {
    val originalFileToPsiFileMap = vFileToViewProviderMap.getAllEntries()
    val fileToPsiFileMap = originalFileToPsiFileMap.toMutableList()
    vFileToViewProviderMap.clear()

    dropInvalidFiles(fileToPsiFileMap)

    vFileToViewProviderMap.replaceAll(fileToPsiFileMap)
    markInvalidations(originalFileToPsiFileMap)

    DebugUtil.performPsiModification<Throwable>("possible invalidate psi after move or delete") {
      fileManager.possiblyInvalidatePhysicalPsi()
    }
  }

  /**
   * removes invalid entries from fileToPsiFileMap and prepares IrrelevantViewProviderReport
   */
  private fun dropInvalidFiles(fileToPsiFileMap: MutableList<FileViewProviderCache.Entry>) {
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

      val psiFile1 = fileManager.findFile(vFile, context) // we need to do findFile to restore missing directories
      if (psiFile1 == null) {
        iterator.remove()
        continue
      }

      FileManagerImpl.clearPsiCaches(viewProvider)
    }
  }

  private fun markInvalidations(originalFileToPsiFileMap: List<FileViewProviderCache.Entry>) {
    if (originalFileToPsiFileMap.isEmpty()) return

    DebugUtil.performPsiModification<Throwable>(null)  {
      for (entry in originalFileToPsiFileMap) {
        val viewProvider = entry.provider
        if (vFileToViewProviderMap.getRaw(entry.file, entry.context) !== viewProvider) {
          fileManager.markInvalidated(viewProvider)
        }
      }
    }
  }
}
