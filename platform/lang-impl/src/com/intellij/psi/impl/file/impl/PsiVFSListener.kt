// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.ide.PsiCopyPasteManager
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.project.stateStore
import com.intellij.psi.*
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.util.FileContentUtilCore
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<PsiVFSListener>()

@Service(Service.Level.PROJECT)
@Internal
class PsiVFSListener internal constructor(private val project: Project) {
  private val myProjectRootManager: ProjectRootManager = ProjectRootManager.getInstance(project)
  private val manager = PsiManager.getInstance(project) as PsiManagerImpl
  private val fileManager = manager.fileManager as FileManagerImpl
  private var reportedUnloadedPsiChange = false

  private fun getCachedDirectory(parent: VirtualFile?): PsiDirectory? {
    return if (parent == null) null else fileManager.getCachedDirectory(parent)
  }

  private fun fileCreated(vFile: VirtualFile) {
    ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
      val parent = vFile.parent
      val parentDir = getCachedDirectory(parent)
      if (parentDir == null) {
        handleVfsChangeWithoutPsi(vFile)
        return@ExternalChangeAction
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
    // do not notify listeners if parent directory was never accessed via PSI
    val parentDir = getCachedDirectory(parent) ?: return

    ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
      val item = (if (vFile.isDirectory) fileManager.findDirectory(vFile) else fileManager.getCachedPsiFile(vFile))
                 ?: return@ExternalChangeAction
      val treeEvent = PsiTreeChangeEventImpl(manager)
      treeEvent.parent = parentDir
      treeEvent.child = item
      manager.beforeChildRemoval(treeEvent)
    })
  }

  // optimization: call myFileManager.removeInvalidFilesAndDirs() once for a group of deletion events, instead of once for each event
  private fun filesDeleted(events: List<VFileEvent>) {
    var needToRemoveInvalidFilesAndDirs = false
    for (event in events) {
      val de = event as VFileDeleteEvent
      val vFile = de.file
      val parent = vFile.parent

      val psiFile = fileManager.getCachedPsiFileInner(vFile)
      var element: PsiElement?
      if (psiFile != null) {
        fileManager.setViewProvider(vFile, null)
        element = psiFile
      }
      else {
        val psiDir = fileManager.getCachedDirectory(vFile)
        if (psiDir != null) {
          needToRemoveInvalidFilesAndDirs = true
          element = psiDir
        }
        else if (parent != null) {
          handleVfsChangeWithoutPsi(parent)
          return
        }
        else {
          element = null
        }
      }
      val parentDir = getCachedDirectory(parent)
      if (element != null && parentDir != null) {
        ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
          val treeEvent = PsiTreeChangeEventImpl(manager)
          treeEvent.parent = parentDir
          treeEvent.child = element
          manager.childRemoved(treeEvent)
        })
      }
    }
    if (needToRemoveInvalidFilesAndDirs) {
      fileManager.removeInvalidFilesAndDirs(false)
    }
  }

  private fun clearViewProvider(vFile: VirtualFile, why: String) {
    DebugUtil.performPsiModification<RuntimeException>(why) { fileManager.setViewProvider(vFile, null) }
  }

  private fun beforePropertyChange(event: VFilePropertyChangeEvent) {
    val vFile = event.file
    val propertyName = event.propertyName

    val viewProvider = fileManager.findCachedViewProvider(vFile)

    val parent = vFile.parent
    val parentDir = if (viewProvider != null && parent != null) fileManager.findDirectory(parent) else getCachedDirectory(parent)
    // do not notifyListeners event if parent directory was never accessed via PSI
    if (parent != null && parentDir == null) {
      return
    }

    ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
      val treeEvent = PsiTreeChangeEventImpl(manager)
      treeEvent.parent = parentDir
      if (propertyName == VirtualFile.PROP_NAME) {
        if (parentDir == null) {
          return@ExternalChangeAction
        }

        val newName = event.newValue as String
        if (vFile.isDirectory) {
          val psiDir = fileManager.findDirectory(vFile)
          if (psiDir != null) {
            if (!FileTypeManager.getInstance().isFileIgnored(newName)) {
              treeEvent.child = psiDir
              treeEvent.propertyName = PsiTreeChangeEvent.PROP_DIRECTORY_NAME
              treeEvent.oldValue = vFile.name
              treeEvent.newValue = newName
              manager.beforePropertyChange(treeEvent)
            }
            else {
              treeEvent.child = psiDir
              manager.beforeChildRemoval(treeEvent)
            }
          }
          else {
            if ((!Registry.`is`("ide.hide.excluded.files") || !isExcludeRoot(vFile)) && !FileTypeManager.getInstance().isFileIgnored(newName)) {
              manager.beforeChildAddition(treeEvent)
            }
          }
        }
        else {
          val viewProvider1 = fileManager.findViewProvider(vFile)
          val psiFile = viewProvider1.getPsi(viewProvider1.baseLanguage)
          val psiFile1 = createFileCopyWithNewName(vFile, newName)

          if (psiFile != null) {
            if (psiFile1 == null) {
              treeEvent.child = psiFile
              manager.beforeChildRemoval(treeEvent)
            }
            else if (psiFile1.javaClass != psiFile.javaClass) {
              treeEvent.oldChild = psiFile
              manager.beforeChildReplacement(treeEvent)
            }
            else {
              treeEvent.child = psiFile
              treeEvent.propertyName = PsiTreeChangeEvent.PROP_FILE_NAME
              treeEvent.oldValue = vFile.name
              treeEvent.newValue = newName
              manager.beforePropertyChange(treeEvent)
            }
          }
          else if (psiFile1 != null) {
            manager.beforeChildAddition(treeEvent)
          }
        }
      }
      else if (propertyName == VirtualFile.PROP_WRITABLE) {
        val psiFile = fileManager.getCachedPsiFileInner(vFile) ?: return@ExternalChangeAction
        treeEvent.element = psiFile
        treeEvent.propertyName = PsiTreeChangeEvent.PROP_WRITABLE
        treeEvent.oldValue = event.oldValue
        treeEvent.newValue = event.newValue
        manager.beforePropertyChange(treeEvent)
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

    val oldFileViewProvider = fileManager.findCachedViewProvider(vFile)
    val oldPsiFile = fileManager.getCachedPsiFile(vFile)

    val parent = vFile.parent
    val parentDir = if (oldPsiFile != null && parent != null) fileManager.findDirectory(parent) else getCachedDirectory(parent)

    if (oldFileViewProvider != null && FileContentUtilCore.FORCE_RELOAD_REQUESTOR == event.requestor) {
      // there is no need to rebuild if there were no PSI in the first place
      fileManager.forceReload(vFile)
      return
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
    ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
      val treeEvent = PsiTreeChangeEventImpl(manager)
      treeEvent.parent = parentDir
      when (propertyName) {
        VirtualFile.PROP_NAME -> {
          if (vFile.isDirectory) {
            val psiDir = fileManager.getCachedDirectory(vFile)
            if (psiDir != null) {
              if (fileTypeManager.isFileIgnored(vFile)) {
                fileManager.removeFilesAndDirsRecursively(vFile)

                treeEvent.child = psiDir
                manager.childRemoved(treeEvent)
              }
              else {
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
                treeEvent.child = psiDir1
                manager.childAdded(treeEvent)
              }
            }
          }
          else {
            val fileViewProvider = fileManager.createFileViewProvider(vFile, true)
            val newPsiFile = fileViewProvider.getPsi(fileViewProvider.baseLanguage)
            if (oldPsiFile != null) {
              if (newPsiFile == null) {
                clearViewProvider(vFile, "PSI renamed")

                treeEvent.child = oldPsiFile
                manager.childRemoved(treeEvent)
              }
              else if (!FileManagerImpl.areViewProvidersEquivalent(fileViewProvider, oldFileViewProvider!!)) {
                fileManager.setViewProvider(vFile, fileViewProvider)

                treeEvent.oldChild = oldPsiFile
                treeEvent.newChild = newPsiFile
                manager.childReplaced(treeEvent)
              }
              else {
                FileManagerImpl.clearPsiCaches(oldFileViewProvider)

                treeEvent.element = oldPsiFile
                treeEvent.propertyName = PsiTreeChangeEvent.PROP_FILE_NAME
                treeEvent.oldValue = event.oldValue
                treeEvent.newValue = event.newValue
                manager.propertyChanged(treeEvent)
              }
            }
            else if (newPsiFile != null) {
              fileManager.setViewProvider(vFile, fileViewProvider)
              if (parentDir != null) {
                treeEvent.child = newPsiFile
                manager.childAdded(treeEvent)
              }
            }
          }
        }
        VirtualFile.PROP_WRITABLE -> {
          if (oldPsiFile == null) {
            return@ExternalChangeAction
          }

          treeEvent.element = oldPsiFile
          treeEvent.propertyName = PsiTreeChangeEvent.PROP_WRITABLE
          treeEvent.oldValue = event.oldValue
          treeEvent.newValue = event.newValue
          manager.propertyChanged(treeEvent)
        }
        VirtualFile.PROP_ENCODING -> {
          if (oldPsiFile == null) {
            return@ExternalChangeAction
          }

          treeEvent.element = oldPsiFile
          treeEvent.propertyName = VirtualFile.PROP_ENCODING
          treeEvent.oldValue = event.oldValue
          treeEvent.newValue = event.newValue
          manager.propertyChanged(treeEvent)
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

    ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
      val treeEvent = PsiTreeChangeEventImpl(manager)
      val isExcluded = vFile.isDirectory && Registry.`is`("ide.hide.excluded.files") && myProjectRootManager.fileIndex.isExcluded(vFile)
      if (oldParentDir != null && !isExcluded) {
        val eventChild = if (vFile.isDirectory) fileManager.findDirectory(vFile) else fileManager.findFile(vFile)
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
      else {
        // checked above
        LOG.assertTrue(newParentDir != null)
        treeEvent.parent = newParentDir
        manager.beforeChildAddition(treeEvent)
      }
    })
  }

  // optimization: call fileManager.removeInvalidFilesAndDirs() once for a group of move events, instead of once for each event
  private fun filesMoved(events: List<VFileEvent>) {
    val oldElements = ArrayList<PsiElement?>(events.size)
    val oldParentDirs = ArrayList<PsiDirectory?>(events.size)
    val newParentDirs = ArrayList<PsiDirectory?>(events.size)

    // find old directories before removing invalid ones
    for (e in events) {
      val event = e as VFileMoveEvent
      val vFile = event.file

      var oldParentDir = fileManager.findDirectory(event.oldParent)
      var newParentDir = fileManager.findDirectory(event.newParent)

      var oldElement: PsiElement? = if (vFile.isDirectory) {
        fileManager.getCachedDirectory(vFile)
      }
      else {
        fileManager.getCachedPsiFileInner(vFile)
      }
      val oldProject = ProjectLocator.getInstance().guessProjectForFile(vFile)
      if (oldProject != null && oldProject !== project) {
        // file moved between projects, remove all associations to the old project
        fileManager.removeFilesAndDirsRecursively(vFile)
        // avoiding crashes in filePointer.getElement()
        PsiCopyPasteManager.getInstance().fileMovedOutsideProject(vFile)
        oldElement = null
        oldParentDir = null
        newParentDir = null
      }
      oldElements.add(oldElement)
      oldParentDirs.add(oldParentDir)
      newParentDirs.add(newParentDir)
    }
    fileManager.removeInvalidFilesAndDirs(true)

    for ((i, event) in events.withIndex()) {
      val vFile = event.file!!

      val oldParentDir = oldParentDirs[i]
      val newParentDir = newParentDirs[i]
      if (oldParentDir == null && newParentDir == null) {
        continue
      }

      val oldElement = oldElements[i]
      var newElement: PsiElement?
      var newViewProvider: FileViewProvider?
      if (vFile.isDirectory) {
        newElement = fileManager.findDirectory(vFile)
        newViewProvider = null
      }
      else {
        newViewProvider = fileManager.createFileViewProvider(vFile, true)
        newElement = newViewProvider.getPsi(fileManager.findViewProvider(vFile).baseLanguage)
      }

      if (oldElement == null && newElement == null) {
        continue
      }

      ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
        val treeEvent = PsiTreeChangeEventImpl(manager)
        if (oldElement == null) {
          fileManager.setViewProvider(vFile, newViewProvider)
          treeEvent.parent = newParentDir
          treeEvent.child = newElement
          manager.childAdded(treeEvent)
        }
        else {
          if (newElement == null) {
            clearViewProvider(vFile, "PSI moved")
            treeEvent.parent = oldParentDir
            treeEvent.child = oldElement
            manager.childRemoved(treeEvent)
          }
          else {
            if (newElement is PsiDirectory ||
                FileManagerImpl.areViewProvidersEquivalent(newViewProvider!!, (oldElement as PsiFile).viewProvider)) {
              treeEvent.oldParent = oldParentDir
              treeEvent.newParent = newParentDir
              treeEvent.child = oldElement
              manager.childMoved(treeEvent)
            }
            else {
              fileManager.setViewProvider(vFile, newViewProvider)
              val treeRemoveEvent = PsiTreeChangeEventImpl(manager)
              treeRemoveEvent.parent = oldParentDir
              treeRemoveEvent.child = oldElement
              manager.childRemoved(treeRemoveEvent)
              val treeAddEvent = PsiTreeChangeEventImpl(manager)
              treeAddEvent.parent = newParentDir
              treeAddEvent.child = newElement
              manager.childAdded(treeAddEvent)
            }
          }
        }
      })
    }
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

  internal fun handleVfsChangeWithoutPsi(vFile: VirtualFile) {
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
      filesMoved(subList)
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

internal class PsiVFSModuleRootListener(private val listenerProject: Project) : ModuleRootListener {
  // accessed from within write action only
  private var depthCounter = 0

  override fun beforeRootsChange(event: ModuleRootEvent) {
    LOG.trace  { "beforeRootsChanged call" }
    if (event.isCausedByFileTypesChange) {
      return
    }

    LOG.trace { "Event is not caused by file types change" }
    ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
      depthCounter++
      LOG.trace { "depthCounter increased $depthCounter" }
      if (depthCounter > 1) {
        return@ExternalChangeAction
      }

      val psiManager = PsiManager.getInstance(listenerProject) as PsiManagerImpl
      val treeEvent = PsiTreeChangeEventImpl(psiManager)
      treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
      psiManager.beforePropertyChange(treeEvent)
    })
  }

  override fun rootsChanged(event: ModuleRootEvent) {
    LOG.trace { "rootsChanged call" }
    val psiManager = PsiManager.getInstance(listenerProject) as PsiManagerImpl
    val fileManager = psiManager.fileManager as FileManagerImpl
    fileManager.dispatchPendingEvents()

    if (event.isCausedByFileTypesChange) {
      return
    }

    LOG.trace { "Event is not caused by file types change" }
    ApplicationManager.getApplication().runWriteAction(
      ExternalChangeAction {
        depthCounter--
        LOG.trace { "depthCounter decreased $depthCounter" }
        assert(depthCounter >= 0) { "unbalanced `beforeRootsChange`/`rootsChanged`: $depthCounter" }
        if (depthCounter > 0) {
          return@ExternalChangeAction
        }

        DebugUtil.performPsiModification<RuntimeException>(null) { fileManager.possiblyInvalidatePhysicalPsi() }

        val treeEvent = PsiTreeChangeEventImpl(psiManager)
        treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
        psiManager.propertyChanged(treeEvent)
      }
    )
  }
}

private class MyFileDocumentManagerListener(private val project: Project) : FileDocumentManagerListener {
  private val fileManager = PsiManagerEx.getInstanceEx(project).fileManager as FileManagerImpl

  override fun fileWithNoDocumentChanged(file: VirtualFile) {
    val viewProvider = fileManager.findCachedViewProvider(file)
    if (viewProvider == null) {
      project.service<PsiVFSListener>().handleVfsChangeWithoutPsi(file)
    }
    else {
      ApplicationManager.getApplication().runWriteAction(ExternalChangeAction {
        if (FileDocumentManagerImpl.recomputeFileTypeIfNecessary(file)) {
          fileManager.forceReload(file)
        }
        else {
          fileManager.reloadPsiAfterTextChange(viewProvider, file)
        }
      })
    }
  }

  override fun fileContentReloaded(file: VirtualFile, document: Document) {
    val psiFile = fileManager.findCachedViewProvider(file)
    if (file.isValid && psiFile != null && FileUtilRt.isTooLarge(file.length) && psiFile !is PsiLargeFile) {
      ApplicationManager.getApplication().runWriteAction(ExternalChangeAction { fileManager.reloadPsiAfterTextChange(psiFile, file) })
    }
  }
}

private val globalListenerInstalled = AtomicBoolean(false)

private fun installGlobalListener() {
  if (!globalListenerInstalled.compareAndSet(false, true)) {
    return
  }

  ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
      for (project in ProjectUtilCore.getOpenProjects()) {
        if (!project.isDisposed) {
          project.service<PsiVFSListener>().before(events)
        }
      }
    }

    override fun after(events: List<VFileEvent>) {
      val projects = ProjectUtilCore.getOpenProjects()
      // let PushedFilePropertiesUpdater process all pending VFS events and update file properties before we issue PSI events
      for (project in projects) {
        val updater = PushedFilePropertiesUpdater.getInstance(project)
        if (updater is PushedFilePropertiesUpdaterImpl) {
          updater.processAfterVfsChanges(events)
        }
      }
      for (project in projects) {
        project.service<PsiVFSListener>().after(events)
      }
    }
  })
}

private class PsiVfsInitProjectActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    val connection = project.messageBus.simpleConnect()

    @Suppress("UsagesOfObsoleteApi")
    serviceAsync<LanguageSubstitutors>().point?.addChangeListener((project as ComponentManagerEx).getCoroutineScope()) {
      if (!project.isDisposed) {
        (PsiManagerEx.getInstanceEx(project).fileManager as FileManagerImpl).processFileTypesChanged(true)
      }
    }

    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, PsiVfsAdditionalLibraryRootListener(project))
    connection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
      override fun fileTypesChanged(e: FileTypeEvent) {
        (PsiManagerEx.getInstanceEx(project).fileManager as FileManagerImpl).processFileTypesChanged(e.removedFileType != null)
      }
    })
    connection.subscribe(FileDocumentManagerListener.TOPIC, MyFileDocumentManagerListener(project))

    connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        (PsiManagerEx.getInstanceEx(project).fileManager as FileManagerImpl).processFileTypesChanged(true)
      }

      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        (PsiManagerEx.getInstanceEx(project).fileManager as FileManagerImpl).processFileTypesChanged(true)
      }
    })

    installGlobalListener()
  }
}

private class PsiVfsAdditionalLibraryRootListener(project: Project) : AdditionalLibraryRootsListener {
  private val psiManager = PsiManager.getInstance(project) as PsiManagerImpl
  private val fileManager = psiManager.fileManager as FileManagerImpl

  override fun libraryRootsChanged(
    presentableLibraryName: @Nls String?,
    oldRoots: Collection<VirtualFile>,
    newRoots: Collection<VirtualFile>,
    libraryNameForDebug: String
  ) {
    ApplicationManager.getApplication().runWriteAction(
      ExternalChangeAction {
        var treeEvent = PsiTreeChangeEventImpl(psiManager)
        treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
        psiManager.beforePropertyChange(treeEvent)
        DebugUtil.performPsiModification<RuntimeException>(null) { fileManager.possiblyInvalidatePhysicalPsi() }

        treeEvent = PsiTreeChangeEventImpl(psiManager)
        treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
        psiManager.propertyChanged(treeEvent)
      }
    )
  }
}