// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl

internal class FileDeletedEventEmitter(
  private val events: List<VFileDeleteEvent>,
  private val fileManager: FileManagerEx,
  private val manager: PsiManagerEx,
  private val listener: PsiVFSListener,
) {
  // optimization: call myFileManager.removeInvalidFilesAndDirs() once for a group of deletion events, instead of once for each event
  fun send() {
    var needToRemoveInvalidFilesAndDirs = false

    for (event in events) {
      val vFile = event.file
      val parent = vFile.parent

      if (vFile.isDirectory) {
        // don't reorder, otherwise dirDeleted won't be called
        needToRemoveInvalidFilesAndDirs = dirDeleted(vFile, parent) || needToRemoveInvalidFilesAndDirs
      }
      else {
        fileDeleted(vFile, parent)
      }
    }

    if (needToRemoveInvalidFilesAndDirs) {
      fileManager.updatePsiAfterVfsMoveOrDelete(false)
    }
  }

  private fun fireChildRemoved(element: PsiElement, parentDir: PsiDirectory?) {
    if (parentDir == null) return
    runWriteActionWithExternalChange {
      val treeEvent = PsiTreeChangeEventImpl(manager)
      treeEvent.parent = parentDir
      treeEvent.child = element
      manager.childRemoved(treeEvent)
    }
  }

  private fun dirDeleted(dir: VirtualFile, parent: VirtualFile?): Boolean {
    val psiDir = fileManager.getCachedDirectory(dir) ?: run {
      listener.handleVfsChangeWithoutPsi(parent)
      return false
    }

    val parentDir = fileManager.getCachedDirectoryNullable(parent)
    fireChildRemoved(psiDir, parentDir)
    return true
  }

  private fun fileDeleted(vFile: VirtualFile, parent: VirtualFile?) {
    val cachedPsiFiles = fileManager.getCachedPsiFilesInner(vFile).ifEmpty {
      listener.handleVfsChangeWithoutPsi(parent)
      return
    }

    val parentDir = fileManager.getCachedDirectoryNullable(parent)
    fileManager.setViewProvider(vFile, null)
    for (psiFile in cachedPsiFiles) {
      fireChildRemoved(psiFile, parentDir)
    }
  }
}
