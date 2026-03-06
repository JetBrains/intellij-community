// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.ide.PsiCopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl

/**
 * Processes file-move [events] and fires corresponding PSI events.
 */
internal class FileMoveEventEmitter(
  private val events: List<VFileMoveEvent>,
  private val fileManager: FileManagerEx,
  private val manager: PsiManagerEx,
  private val project: Project,
) {

  private data class EventInfo(
    val event: VFileMoveEvent,
    val oldElements: List<PsiElement>,
    val oldParentDir: PsiDirectory?,
    val newParentDir: PsiDirectory?,
  )

  fun send() {
    // find old directories before removing invalid ones
    val infos = prepareEventInfo()

    fileManager.removeInvalidFilesAndDirs(true)

    for (info in infos) {
      processSingleMoveEvent(info)
    }
  }

  private fun prepareEventInfo(): List<EventInfo> {
    return events.map { e ->
      val vFile = e.file

      var oldParentDir = fileManager.findDirectory(e.oldParent)
      var newParentDir = fileManager.findDirectory(e.newParent)

      var oldElements = if (vFile.isDirectory) {
        listOfNotNull(fileManager.getCachedDirectory(vFile))
      }
      else {
        fileManager.getCachedPsiFilesInner(vFile)
      }

      val oldProject = ProjectLocator.getInstance().guessProjectForFile(vFile)
      if (oldProject != null && oldProject !== project) {
        // file moved between projects, remove all associations to the old project
        fileManager.removeFilesAndDirsRecursively(vFile)
        // avoiding crashes in filePointer.getElement()
        PsiCopyPasteManager.getInstance().fileMovedOutsideProject(vFile)
        oldElements = emptyList()
        oldParentDir = null
        newParentDir = null
      }

      EventInfo(e, oldElements, oldParentDir, newParentDir)
    }
  }

  private fun prepareNewPsiAndViewProvider(vFile: VirtualFile): Pair<PsiFileSystemItem?, FileViewProvider?> {
    if (vFile.isDirectory) {
      // directory
      return fileManager.findDirectory(vFile) to null
    }
    else {
      // normal file
      val newViewProvider = fileManager.createFileViewProvider(vFile, true)
      val newElement = newViewProvider.getPsi(fileManager.findViewProvider(vFile).baseLanguage)
      return newElement to newViewProvider
    }
  }

  private fun processSingleMoveEvent(psi: EventInfo) {
    val (event, oldElements, oldParentDir, newParentDir) = psi

    if (oldParentDir == null && newParentDir == null) {
      return
    }

    val vFile = event.file

    val (newElement: PsiElement?, newViewProvider: FileViewProvider?) = prepareNewPsiAndViewProvider(vFile)

    if (oldElements.isEmpty() && newElement == null) {
      return
    }

    runWriteActionWithExternalChange {
      doSendMoveEvents(vFile, oldParentDir, newParentDir, oldElements, newViewProvider, newElement)
    }
  }

  fun doSendMoveEvents(
    vFile: VirtualFile,
    oldParentDir: PsiDirectory?,
    newParentDir: PsiDirectory?,
    oldElements: List<PsiElement>,
    newViewProvider: FileViewProvider?,
    newElement: PsiElement?,
  ) {
    if (oldElements.isEmpty()) {
      fireFileCreated(newViewProvider, vFile, newParentDir, newElement)
    }
    else {
      if (newElement == null) {
        fireFileRemoved(vFile, oldElements, oldParentDir)
      }
      else {
        if (newElement is PsiDirectory ||
            FileManagerImpl.areViewProvidersEquivalent(newViewProvider!!, (oldElements.first() as PsiFile).viewProvider)) {
          firePlainMove(oldElements, oldParentDir, newParentDir)
        }
        else {
          fireOldRemovedNewCreated(vFile, newViewProvider, oldElements, oldParentDir, newParentDir, newElement)
        }
      }
    }
  }

  private fun fireOldRemovedNewCreated(
    vFile: VirtualFile,
    newViewProvider: FileViewProvider,
    oldElements: List<PsiElement>,
    oldParentDir: PsiDirectory?,
    newParentDir: PsiDirectory?,
    newElement: PsiElement,
  ) {
    fileManager.setViewProvider(vFile, newViewProvider)

    for (oldElement in oldElements) {
      val treeRemoveEvent = PsiTreeChangeEventImpl(manager)
      treeRemoveEvent.parent = oldParentDir
      treeRemoveEvent.child = oldElement
      manager.childRemoved(treeRemoveEvent)
    }

    val treeAddEvent = PsiTreeChangeEventImpl(manager)
    treeAddEvent.parent = newParentDir
    treeAddEvent.child = newElement
    manager.childAdded(treeAddEvent)
  }

  private fun firePlainMove(
    oldElements: List<PsiElement>,
    oldParentDir: PsiDirectory?,
    newParentDir: PsiDirectory?,
  ) {
    for (oldElement in oldElements) {
      if (oldElement.isValid) { // fileManager.removeInvalidFilesAndDirs(true) must have already invalided all old elements that must die.
        val treeEvent = PsiTreeChangeEventImpl(manager)
        treeEvent.oldParent = oldParentDir
        treeEvent.newParent = newParentDir
        treeEvent.child = oldElement
        manager.childMoved(treeEvent)
      }
      else {
        val treeEvent = PsiTreeChangeEventImpl(manager)
        treeEvent.parent = oldParentDir
        treeEvent.child = oldElement
        manager.childRemoved(treeEvent)
      }
    }
  }

  private fun fireFileRemoved(
    vFile: VirtualFile,
    oldElements: List<PsiElement>,
    oldParentDir: PsiDirectory?,
  ) {
    clearViewProvider(fileManager, vFile, "PSI moved")

    for (oldElement in oldElements) {
      val treeEvent = PsiTreeChangeEventImpl(manager)
      treeEvent.parent = oldParentDir
      treeEvent.child = oldElement
      manager.childRemoved(treeEvent)
    }
  }

  private fun fireFileCreated(
    newViewProvider: FileViewProvider?,
    vFile: VirtualFile,
    newParentDir: PsiDirectory?,
    newElement: PsiElement?,
  ) {
    if (newViewProvider != null) {
      fileManager.setViewProvider(vFile, newViewProvider)
    }
    val treeEvent = PsiTreeChangeEventImpl(manager)
    treeEvent.parent = newParentDir
    treeEvent.child = newElement
    manager.childAdded(treeEvent)
  }
}
