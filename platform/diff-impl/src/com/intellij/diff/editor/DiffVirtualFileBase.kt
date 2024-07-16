// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffWindowBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.docking.impl.DockManagerImpl

abstract class DiffVirtualFileBase(name: String) :
  LightVirtualFile(name, DiffFileType.INSTANCE, ""),
  DiffContentVirtualFile, VirtualFileWithoutContent {
  init {
    useDiffWindowDimensionKey()
    turnOffReopeningWindow()
  }

  override fun isWritable(): Boolean = false

  override fun toString(): String = "${javaClass.name}@${Integer.toHexString(hashCode())}"

  /**
   * See [DiffEditorEscapeAction]
   */
  open fun createEscapeHandler(): AnAction? {
    return getUserData(ESCAPE_HANDLER)
  }

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
