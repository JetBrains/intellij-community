// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.project.stateStore
import com.intellij.psi.ExternalChangeActionUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import one.util.streamex.StreamEx

private val LOG = logger<PsiVFSListener>()

@Service(Service.Level.PROJECT)
internal class PsiVFSListener(private val project: Project) {
  private val myProjectRootManager: ProjectRootManager = ProjectRootManager.getInstance(project)
  private val manager = PsiManagerEx.getInstanceEx(project)
  private val fileManager = manager.fileManager as FileManagerEx
  private var reportedUnloadedPsiChange = false

  private fun fileCreated(vFile: VirtualFile) {
    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      val parent = vFile.parent
      val parentDir = fileManager.getCachedDirectory(parent)
      if (parentDir == null) {
        handleVfsChangeWithoutPsi(vFile)
        return@externalChangeAction
      }

      val item = if (vFile.isDirectory) fileManager.findDirectory(vFile) else fileManager.findFile(vFile)
      if (item != null && item.project === manager.project) {
        val treeEvent = PsiTreeChangeEventImpl(manager)
        treeEvent.parent = parentDir
        manager.beforeChildAddition(treeEvent)
        treeEvent.child = item
        manager.childAdded(treeEvent)
      }
    })
  }

  private fun beforeFileDeletion(event: VFileDeleteEvent) {
    val vFile = event.file

    val parent = vFile.parent
    // do not notify listeners if the parent directory was never accessed via PSI
    val parentDir = fileManager.getCachedDirectoryNullable(parent) ?: return

    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      val items = if (vFile.isDirectory) listOfNotNull(fileManager.findDirectory(vFile)) else fileManager.getCachedPsiFiles(vFile)
      for (item in items) {
        val treeEvent = PsiTreeChangeEventImpl(manager)
        treeEvent.parent = parentDir
        treeEvent.child = item
        manager.beforeChildRemoval(treeEvent)
      }
    })
  }

  // optimization: call myFileManager.removeInvalidFilesAndDirs() once for a group of deletion events, instead of once for each event
  private fun filesDeleted(events: List<VFileEvent>) {
    var needToRemoveInvalidFilesAndDirs = false

    fun fireChildRemoved(element: PsiElement, parentDir: PsiDirectory?) {
      if (parentDir == null) return
      ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
        val treeEvent = PsiTreeChangeEventImpl(manager)
        treeEvent.parent = parentDir
        treeEvent.child = element
        manager.childRemoved(treeEvent)
      })
    }

    fun dirDeleted(dir: VirtualFile, parent: VirtualFile?) {
      val psiDir = fileManager.getCachedDirectory(dir) ?: run {
        handleVfsChangeWithoutPsi(parent)
        return
      }

      val parentDir = fileManager.getCachedDirectoryNullable(parent)
      fireChildRemoved(psiDir, parentDir)
      needToRemoveInvalidFilesAndDirs = true
    }

    fun fileDeleted(vFile: VirtualFile, parent: VirtualFile?) {
      val cachedPsiFiles = fileManager.getCachedPsiFilesInner(vFile).ifEmpty {
        handleVfsChangeWithoutPsi(parent)
        return
      }

      val parentDir = fileManager.getCachedDirectoryNullable(parent)
      fileManager.setViewProvider(vFile, null)
      for (psiFile in cachedPsiFiles) {
        fireChildRemoved(psiFile, parentDir)
      }
    }

    for (event in events) {
      val de = event as VFileDeleteEvent
      val vFile = de.file
      val parent = vFile.parent

      if (vFile.isDirectory) {
        dirDeleted(vFile, parent)
      }
      else {
        fileDeleted(vFile, parent)
      }
    }

    if (needToRemoveInvalidFilesAndDirs) {
      fileManager.removeInvalidFilesAndDirs(false)
    }
  }

  private fun beforePropertyChange(event: VFilePropertyChangeEvent) {
    BeforePropertyChangeEventEmitter(event, fileManager, manager, project, myProjectRootManager).send()
  }

  private fun propertyChanged(event: VFilePropertyChangeEvent) {
    PropertyChangeEventEmitter(event, fileManager, manager, this).send()
  }

  private fun beforeFileMovement(event: VFileMoveEvent) {
    val vFile = event.file

    val oldParentDir = fileManager.findDirectory(event.oldParent)
    val newParentDir = fileManager.findDirectory(event.newParent)
    if ((oldParentDir == null && newParentDir == null) || FileTypeManager.getInstance().isFileIgnored(vFile)) {
      return
    }

    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      val isExcluded = vFile.isDirectory && Registry.`is`("ide.hide.excluded.files") && myProjectRootManager.fileIndex.isExcluded(vFile)
      if (oldParentDir != null && !isExcluded) {

        // a list of:
        // - one or several not-null PsiFiles
        // - one not-null PsiDirectory
        // - single 'null' if corresponding PsiDirectory or PsiFile cannot be created
        val eventChildren: List<PsiFileSystemItem?> = if (vFile.isDirectory) {
          listOf(fileManager.findDirectory(vFile))
        }
        else {
          fileManager.getCachedPsiFiles(vFile).ifEmpty { listOf(fileManager.findFile(vFile)) }
        }

        for (eventChild in eventChildren) {
          val treeEvent = PsiTreeChangeEventImpl(manager)
          treeEvent.child = eventChild
          if (newParentDir != null) {
            treeEvent.oldParent = oldParentDir
            treeEvent.newParent = newParentDir
            manager.beforeChildMovement(treeEvent)
          }
          else {
            treeEvent.parent = oldParentDir
            manager.beforeChildRemoval(treeEvent)
          }
        }
      }
      else {
        // checked above
        LOG.assertTrue(newParentDir != null)
        val treeEvent = PsiTreeChangeEventImpl(manager)
        treeEvent.parent = newParentDir
        manager.beforeChildAddition(treeEvent)
      }
    })
  }

  // optimization: call fileManager.removeInvalidFilesAndDirs() once for a group of move events, instead of once for each event
  private fun filesMoved(events: List<VFileMoveEvent>) {
    FileMoveEventEmitter(events, fileManager, manager, project).send()
  }

  fun handleVfsChangeWithoutPsi(vFile: VirtualFile?) {
    if (vFile == null) {
      return
    }

    if (!reportedUnloadedPsiChange && isInRootModel(vFile)) {
      fileManager.firePropertyChangedForUnloadedPsi()
      reportedUnloadedPsiChange = true
    }
  }

  private fun isInRootModel(file: VirtualFile): Boolean {
    if (project.stateStore.isProjectFile(file)) {
      return false
    }

    val index = ProjectFileIndex.getInstance(project)
    return index.isInContent(file) || index.isInLibrary(file)
  }

  fun before(events: List<VFileEvent>) {
    reportedUnloadedPsiChange = false
    for (event in events) {
      when (event) {
        is VFileDeleteEvent -> beforeFileDeletion(event)
        is VFilePropertyChangeEvent -> beforePropertyChange(event)
        is VFileMoveEvent -> beforeFileMovement(event)
      }
    }
  }

  fun after(events: List<VFileEvent>) {
    groupAndFire(events)
    reportedUnloadedPsiChange = false
  }

  // grouping events of the same type together and calling fireForGrouped() for each batch
  private fun groupAndFire(events: List<VFileEvent>) {
    // group several VFileDeleteEvents together, several VFileMoveEvents together, place all other events into one-element lists
    StreamEx.of(events)
      .groupRuns { e1, e2 -> e1 is VFileDeleteEvent && e2 is VFileDeleteEvent || e1 is VFileMoveEvent && e2 is VFileMoveEvent }
      .forEach { fireForGrouped(it) }
  }

  private fun fireForGrouped(subList: List<VFileEvent>) {
    val event = subList[0]
    if (event is VFileDeleteEvent) {
      DebugUtil.performPsiModification<RuntimeException>(null) { filesDeleted(subList) }
    }
    else if (event is VFileMoveEvent) {
      @Suppress("UNCHECKED_CAST")
      filesMoved(subList as List<VFileMoveEvent>)
    }
    else {
      assert(subList.size == 1)
      if (event is VFileCopyEvent) {
        val copy = event.newParent.findChild(event.newChildName)
        if (copy != null) {
          // no need to group file creation events
          fileCreated(copy)
        }
      }
      else if (event is VFileCreateEvent) {
        val file = event.getFile()
        if (file != null) {
          // no need to group file creation events
          fileCreated(file)
        }
      }
      else if (event is VFilePropertyChangeEvent) {
        propertyChanged(event)
      }
    }
  }
}
