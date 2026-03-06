// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.ExternalChangeActionUtil
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.util.FileContentUtilCore

internal class PropertyChangeEventEmitter(
  private val event: VFilePropertyChangeEvent,
  private val fileManager: FileManagerEx,
  private val manager: PsiManagerEx,
  private val listener: PsiVFSListener,
) {
  fun send() {
    val propertyName = event.propertyName
    val vFile = event.file

    val oldFileViewProviders = fileManager.findCachedViewProviders(vFile)
    if (oldFileViewProviders.isNotEmpty() && FileContentUtilCore.FORCE_RELOAD_REQUESTOR == event.requestor) {
      // there is no need to rebuild if there were no PSI in the first place
      fileManager.forceReload(vFile)
      return
    }

    val oldPsiFiles = fileManager.getCachedPsiFiles(vFile)
    val parentDir = findParentDir(vFile, oldPsiFiles)

    // do not suppress reparse request for light files
    if (parentDir == null) {
      var fire = VirtualFile.PROP_NAME == propertyName && vFile.isDirectory
      if (fire) {
        val psiDir = fileManager.getCachedDirectory(vFile)
        fire = psiDir != null
      }
      if (!fire && VirtualFile.PROP_WRITABLE != propertyName) {
        listener.handleVfsChangeWithoutPsi(vFile)
        return
      }
    }

    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      when (propertyName) {
        VirtualFile.PROP_NAME -> sendRenameEvents(vFile, parentDir, oldPsiFiles, oldFileViewProviders)
        VirtualFile.PROP_WRITABLE -> sendWriteAccessChangeEvent(oldPsiFiles, parentDir)
        VirtualFile.PROP_ENCODING -> sendEncodingChangeEvent(oldPsiFiles, parentDir)
      }
    })
  }

  private fun sendEncodingChangeEvent(oldPsiFiles: List<PsiFile>, parentDir: PsiDirectory?) {
    if (oldPsiFiles.isEmpty()) {
      return
    }

    for (oldPsiFile in oldPsiFiles) {
      val treeEvent = PsiTreeChangeEventImpl(manager)
      treeEvent.parent = parentDir
      treeEvent.element = oldPsiFile
      treeEvent.propertyName = VirtualFile.PROP_ENCODING
      treeEvent.oldValue = event.oldValue
      treeEvent.newValue = event.newValue
      manager.propertyChanged(treeEvent)
    }
  }

  private fun sendWriteAccessChangeEvent(oldPsiFiles: List<PsiFile>, parentDir: PsiDirectory?) {
    if (oldPsiFiles.isEmpty()) {
      return
    }

    for (oldPsiFile in oldPsiFiles) {
      val treeEvent = PsiTreeChangeEventImpl(manager)
      treeEvent.parent = parentDir
      treeEvent.element = oldPsiFile
      treeEvent.propertyName = PsiTreeChangeEvent.PROP_WRITABLE
      treeEvent.oldValue = event.oldValue
      treeEvent.newValue = event.newValue
      manager.propertyChanged(treeEvent)
    }
  }

  private fun sendRenameEvents(
    vFile: VirtualFile,
    parentDir: PsiDirectory?,
    oldPsiFiles: List<PsiFile>,
    oldFileViewProviders: List<FileViewProvider>,
  ) {
    if (vFile.isDirectory) {
      val psiDir = fileManager.getCachedDirectory(vFile)
      if (psiDir != null) {
        if (FileTypeManager.getInstance().isFileIgnored(vFile)) {
          fileManager.removeFilesAndDirsRecursively(vFile)

          val treeEvent = PsiTreeChangeEventImpl(manager)
          treeEvent.parent = parentDir
          treeEvent.child = psiDir
          manager.childRemoved(treeEvent)
        }
        else {
          val treeEvent = PsiTreeChangeEventImpl(manager)
          treeEvent.parent = parentDir
          treeEvent.element = psiDir
          treeEvent.propertyName = PsiTreeChangeEvent.PROP_DIRECTORY_NAME
          treeEvent.oldValue = event.oldValue
          treeEvent.newValue = event.newValue
          manager.propertyChanged(treeEvent)
        }
      }
      else {
        val psiDir1 = fileManager.findDirectory(vFile)
        if (psiDir1 != null) {
          val treeEvent = PsiTreeChangeEventImpl(manager)
          treeEvent.parent = parentDir
          treeEvent.child = psiDir1
          manager.childAdded(treeEvent)
        }
      }
    }
    else {
      val newFileViewProvider = fileManager.createFileViewProvider(vFile, true)
      val newPsiFile = newFileViewProvider.getPsi(newFileViewProvider.baseLanguage)
      if (oldPsiFiles.isNotEmpty()) {
        if (newPsiFile == null) {
          clearViewProvider(fileManager, vFile, "PSI renamed")

          for (oldPsiFile in oldPsiFiles) {
            val treeEvent = PsiTreeChangeEventImpl(manager)
            treeEvent.parent = parentDir
            treeEvent.child = oldPsiFile
            manager.childRemoved(treeEvent)
          }
        }
        else {
          // todo IJPL-339 do we want to select a preferred view provider instead of the first one???
          val firstOldViewProvider = oldFileViewProviders.first()
          if (!FileManagerImpl.areViewProvidersEquivalent(newFileViewProvider, firstOldViewProvider)) {
            // the file has changed its view provider factory
            // we need to delete all old providers and create one new provider.

            fileManager.setViewProvider(vFile, newFileViewProvider)

            run {
              val treeEvent = PsiTreeChangeEventImpl(manager)
              treeEvent.parent = parentDir
              treeEvent.oldChild = firstOldViewProvider.getPsi(firstOldViewProvider.baseLanguage)
              treeEvent.newChild = newPsiFile
              manager.childReplaced(treeEvent)
            }

            for (oldFileViewProvider in oldFileViewProviders.drop(1)) {
              val treeEvent = PsiTreeChangeEventImpl(manager)
              treeEvent.parent = parentDir
              treeEvent.oldChild = oldFileViewProvider.getPsi(oldFileViewProvider.baseLanguage)
              manager.childRemoved(treeEvent)
            }
          }
          else {
            // the file keeps the same view provider factory
            // let's reuse all old view providers

            for (oldFileViewProvider in oldFileViewProviders) {
              FileManagerImpl.clearPsiCaches(oldFileViewProvider)
            }

            for (oldPsiFile in oldPsiFiles) {
              val treeEvent = PsiTreeChangeEventImpl(manager)
              treeEvent.parent = parentDir
              treeEvent.element = oldPsiFile
              treeEvent.propertyName = PsiTreeChangeEvent.PROP_FILE_NAME
              treeEvent.oldValue = event.oldValue
              treeEvent.newValue = event.newValue
              manager.propertyChanged(treeEvent)
            }
          }
        }
      }
      else if (newPsiFile != null) {
        fileManager.setViewProvider(vFile, newFileViewProvider)
        if (parentDir != null) {
          val treeEvent = PsiTreeChangeEventImpl(manager)
          treeEvent.parent = parentDir
          treeEvent.child = newPsiFile
          manager.childAdded(treeEvent)
        }
      }
    }
  }

  private fun findParentDir(
    vFile: VirtualFile,
    oldPsiFiles: List<PsiFile>,
  ): PsiDirectory? {
    val parent = vFile.parent
    return if (oldPsiFiles.isNotEmpty() && parent != null)
      fileManager.findDirectory(parent)
    else
      fileManager.getCachedDirectoryNullable(parent)
  }
}
