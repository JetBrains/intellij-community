// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter

abstract class FilesModificationTrackerBase(val project: Project) : SimpleModificationTracker(), Disposable {

  init {
    PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeAnyChangeAbstractAdapter() {
      override fun onChange(file: PsiFile?) {
        val virtualFile = file?.virtualFile ?: return
        handleChange(virtualFile)
      }
    }, this)

    val messageBus = project.messageBus.connect(this)
    messageBus.subscribe(VirtualFileManager.VFS_CHANGES, BulkVirtualFileListenerAdapter(object : VirtualFileListener {
      override fun propertyChanged(event: VirtualFilePropertyEvent) {
        handleChange(event.file)
      }

      override fun contentsChanged(event: VirtualFileEvent) {
        handleChange(event.file)
      }

      override fun fileCreated(event: VirtualFileEvent) {
        handleChange(event.file)
      }

      override fun beforeFileDeletion(event: VirtualFileEvent) {
        handleChange(event.file)
      }

      override fun fileMoved(event: VirtualFileMoveEvent) {
        handleChange(event.file)
      }

      override fun fileCopied(event: VirtualFileCopyEvent) {
        handleChange(event.file)
      }
    }))
  }

  abstract fun isFileSupported(virtualFile: VirtualFile): Boolean

  private fun handleChange(virtualFile: VirtualFile) {
    if (isFileSupported(virtualFile)) {
      incModificationCount()
    }
  }

  override fun dispose() {
  }
}