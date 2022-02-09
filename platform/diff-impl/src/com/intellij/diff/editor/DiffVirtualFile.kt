// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffWindowBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.docking.impl.DockManagerImpl

abstract class DiffVirtualFile(name: String) :
  LightVirtualFile(name, DiffFileType.INSTANCE, ""),
  DiffContentVirtualFile, VirtualFileWithoutContent, IdeDocumentHistoryImpl.SkipFromDocumentHistory {

  init {
    useDiffWindowDimensionKey()
    turnOffReopeningWindow()
  }

  abstract fun createProcessor(project: Project): DiffRequestProcessor

  override fun isWritable(): Boolean = false

  override fun toString(): String = "${javaClass.name}@${Integer.toHexString(hashCode())}"

  companion object {

    fun VirtualFile.useDiffWindowDimensionKey() = putUserData(DockManagerImpl.WINDOW_DIMENSION_KEY, DiffWindowBase.DEFAULT_DIALOG_GROUP_KEY)
    fun VirtualFile.turnOffReopeningWindow() = putUserData(DockManagerImpl.REOPEN_WINDOW, false)

    /**
     * @see [DiffEditorEscapeAction]
     */
    @JvmField
    val ESCAPE_HANDLER = Key<AnAction?>("ESCAPE_HANDLER")
  }
}
