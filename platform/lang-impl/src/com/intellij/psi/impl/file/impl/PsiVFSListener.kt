// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
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
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.util.FileContentUtilCore
import one.util.streamex.StreamEx

private val LOG = logger<PsiVFSListener>()

@Service(Service.Level.PROJECT)
internal class PsiVFSListener(private val project: Project) {
  private val myProjectRootManager: ProjectRootManager = ProjectRootManager.getInstance(project)
  private val manager = PsiManagerEx.getInstanceEx(project)
  private val fileManager = manager.fileManager as FileManagerEx
  private var reportedUnloadedPsiChange = false

  private fun getCachedDirectory(parent: VirtualFile?): PsiDirectory? {
    return if (parent == null) null else fileManager.getCachedDirectory(parent)
  }

  private fun fileCreated(vFile: VirtualFile) {
    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      val parent = vFile.parent
      val parentDir = getCachedDirectory(parent)
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
    val parentDir = getCachedDirectory(parent) ?: return

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

      val parentDir = getCachedDirectory(parent)
      fireChildRemoved(psiDir, parentDir)
      needToRemoveInvalidFilesAndDirs = true
    }

    fun fileDeleted(vFile: VirtualFile, parent: VirtualFile?) {
      val cachedPsiFiles = fileManager.getCachedPsiFilesInner(vFile).ifEmpty {
        handleVfsChangeWithoutPsi(parent)
        return
      }

      val parentDir = getCachedDirectory(parent)
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
    val vFile = event.file
    val propertyName = event.propertyName
    val parent = vFile.parent
    val parentDir = run {
      if (parent == null || fileManager.findCachedViewProvider(vFile) == null) {
        getCachedDirectory(parent)
      }
      else {
        fileManager.findDirectory(parent)
      }
    }
    // do not notifyListeners event if the parent directory was never accessed via PSI
    if (parent != null && parentDir == null) {
      return
    }

    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      if (propertyName == VirtualFile.PROP_NAME) {
        if (parentDir == null) {
          return@externalChangeAction
        }

        val newName = event.newValue as String
        if (vFile.isDirectory) {
          val psiDir = fileManager.findDirectory(vFile)
          if (psiDir != null) {
            if (!FileTypeManager.getInstance().isFileIgnored(newName)) {
              val treeEvent = PsiTreeChangeEventImpl(manager)
              treeEvent.parent = parentDir
              treeEvent.child = psiDir
              treeEvent.propertyName = PsiTreeChangeEvent.PROP_DIRECTORY_NAME
              treeEvent.oldValue = vFile.name
              treeEvent.newValue = newName
              manager.beforePropertyChange(treeEvent)
            }
            else {
              val treeEvent = PsiTreeChangeEventImpl(manager)
              treeEvent.parent = parentDir
              treeEvent.child = psiDir
              manager.beforeChildRemoval(treeEvent)
            }
          }
          else {
            if ((!Registry.`is`("ide.hide.excluded.files") || !isExcludeRoot(vFile)) && !FileTypeManager.getInstance().isFileIgnored(newName)) {
              val treeEvent = PsiTreeChangeEventImpl(manager)
              treeEvent.parent = parentDir
              manager.beforeChildAddition(treeEvent)
            }
          }
        }
        else {
          val viewProviders = fileManager.findCachedViewProviders(vFile).ifEmpty { listOf(fileManager.findViewProvider(vFile)) }
          for (viewProvider in viewProviders) {
            val psiFile = viewProvider.getPsi(fileManager.findViewProvider(vFile).baseLanguage)
            val psiFile1 = createFileCopyWithNewName(vFile, newName)

            if (psiFile != null) {
              if (psiFile1 == null) {
                val treeEvent = PsiTreeChangeEventImpl(manager)
                treeEvent.parent = parentDir
                treeEvent.child = psiFile
                manager.beforeChildRemoval(treeEvent)
              }
              else if (psiFile1.javaClass != psiFile.javaClass) {
                val treeEvent = PsiTreeChangeEventImpl(manager)
                treeEvent.parent = parentDir
                treeEvent.oldChild = psiFile
                manager.beforeChildReplacement(treeEvent)
              }
              else {
                val treeEvent = PsiTreeChangeEventImpl(manager)
                treeEvent.parent = parentDir
                treeEvent.child = psiFile
                treeEvent.propertyName = PsiTreeChangeEvent.PROP_FILE_NAME
                treeEvent.oldValue = vFile.name
                treeEvent.newValue = newName
                manager.beforePropertyChange(treeEvent)
              }
            }
            else if (psiFile1 != null) {
              val treeEvent = PsiTreeChangeEventImpl(manager)
              treeEvent.parent = parentDir
              manager.beforeChildAddition(treeEvent)
            }
          }
        }
      }
      else if (propertyName == VirtualFile.PROP_WRITABLE) {
        val psiFiles = fileManager.getCachedPsiFilesInner(vFile).ifEmpty {
          return@externalChangeAction
        }

        for (psiFile in psiFiles) {
          val treeEvent = PsiTreeChangeEventImpl(manager)
          treeEvent.parent = parentDir
          treeEvent.element = psiFile
          treeEvent.propertyName = PsiTreeChangeEvent.PROP_WRITABLE
          treeEvent.oldValue = event.oldValue
          treeEvent.newValue = event.newValue
          manager.beforePropertyChange(treeEvent)
        }
      }
    })
  }

  private fun isExcludeRoot(file: VirtualFile): Boolean {
    val parent = file.parent ?: return false
    val module = myProjectRootManager.fileIndex.getModuleForFile(parent) ?: return false
    val excludeRoots = ModuleRootManager.getInstance(module).excludeRoots
    return excludeRoots.contains(file)
  }

  private fun propertyChanged(event: VFilePropertyChangeEvent) {
    val propertyName = event.propertyName
    val vFile = event.file

    val oldFileViewProviders = fileManager.findCachedViewProviders(vFile)
    if (oldFileViewProviders.isNotEmpty() && FileContentUtilCore.FORCE_RELOAD_REQUESTOR == event.requestor) {
      // there is no need to rebuild if there were no PSI in the first place
      fileManager.forceReload(vFile)
      return
    }

    val oldPsiFiles = fileManager.getCachedPsiFiles(vFile)
    val parentDir = run {
      val parent = vFile.parent
      if (oldPsiFiles.isNotEmpty() && parent != null)
        fileManager.findDirectory(parent)
      else
        getCachedDirectory(parent)
    }

    // do not suppress reparse request for light files
    if (parentDir == null) {
      var fire = VirtualFile.PROP_NAME == propertyName && vFile.isDirectory
      if (fire) {
        val psiDir = fileManager.getCachedDirectory(vFile)
        fire = psiDir != null
      }
      if (!fire && VirtualFile.PROP_WRITABLE != propertyName) {
        handleVfsChangeWithoutPsi(vFile)
        return
      }
    }

    val fileTypeManager = FileTypeManager.getInstance()
    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      when (propertyName) {
        VirtualFile.PROP_NAME -> {
          if (vFile.isDirectory) {
            val psiDir = fileManager.getCachedDirectory(vFile)
            if (psiDir != null) {
              if (fileTypeManager.isFileIgnored(vFile)) {
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
        VirtualFile.PROP_WRITABLE -> {
          if (oldPsiFiles.isEmpty()) {
            return@externalChangeAction
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
        VirtualFile.PROP_ENCODING -> {
          if (oldPsiFiles.isEmpty()) {
            return@externalChangeAction
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
      }
    })
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

  private fun createFileCopyWithNewName(vFile: VirtualFile, name: String): PsiFile? {
    // TODO[ik] remove this. Event handling and generation must be in view providers mechanism since we
    // need to track changes in _all_ psi views (e.g. namespace changes in XML)
    val instance = FileTypeManager.getInstance()
    if (instance.isFileIgnored(name)) {
      return null
    }

    val fileTypeByFileName = instance.getFileTypeByFileName(name)
    return PsiFileFactory.getInstance(manager.project).createFileFromText(
      /* name = */ name,
      /* fileType = */ fileTypeByFileName,
      /* text = */ "",
      /* modificationStamp = */ vFile.modificationStamp,
      /* eventSystemEnabled = */ true,
      /* markAsCopy = */ false,
    )
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
