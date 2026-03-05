// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFile.PROP_NAME
import com.intellij.openapi.vfs.VirtualFile.PROP_WRITABLE
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.ExternalChangeActionUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl

internal class BeforePropertyChangeEventEmitter(
  val event: VFilePropertyChangeEvent,
  val fileManager: FileManagerEx,
  val manager: PsiManagerEx,
  val project: Project,
  val myProjectRootManager: ProjectRootManager,
) {
  fun send() {
    val vFile = event.file
    val parent: VirtualFile? = vFile.parent
    val parentDir = findParentDir(parent, vFile)
    // do not notifyListeners event if the parent directory was never accessed via PSI
    if (parent != null && parentDir == null) {
      return
    }

    ApplicationManager.getApplication().runWriteAction(ExternalChangeActionUtil.externalChangeAction {
      when (event.propertyName) {
        PROP_NAME -> sendRenameEvent(parentDir, vFile)
        PROP_WRITABLE -> sendWritePermissionChangeEvent(parentDir, vFile)
      }
    })
  }

  private fun findParentDir(
    parent: VirtualFile?,
    vFile: VirtualFile,
  ): PsiDirectory? = if (parent == null || fileManager.findCachedViewProvider(vFile) == null) {
    fileManager.getCachedDirectoryNullable(parent)
  }
  else {
    fileManager.findDirectory(parent)
  }

  /**
   * Handles [PROP_WRITABLE] event
   */
  private fun sendWritePermissionChangeEvent(parentDir: PsiDirectory?, vFile: VirtualFile) {
    val psiFiles = fileManager.getCachedPsiFilesInner(vFile).ifEmpty {
      return
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

  /**
   * Handles [PROP_NAME] event
   */
  private fun sendRenameEvent(parentDir: PsiDirectory?, vFile: VirtualFile) {
    if (parentDir == null) {
      return
    }

    val newName = event.newValue as String
    if (vFile.isDirectory) {
      sendRenameEventForDirectory(vFile, newName, parentDir)
    }
    else {
      sendRenameEventForFile(vFile, newName, parentDir)
    }
  }

  private fun sendRenameEventForFile(vFile: VirtualFile, newName: String, parentDir: PsiDirectory) {
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

  private fun sendRenameEventForDirectory(vFile: VirtualFile, newName: String, parentDir: PsiDirectory) {
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

  private fun isExcludeRoot(file: VirtualFile): Boolean {
    val parent = file.parent ?: return false
    val module = myProjectRootManager.fileIndex.getModuleForFile(parent) ?: return false
    val excludeRoots = ModuleRootManager.getInstance(module).excludeRoots
    return excludeRoots.contains(file)
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
}
