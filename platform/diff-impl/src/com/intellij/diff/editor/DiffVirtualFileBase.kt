// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.impl.DiffSettingsHolder.IncludeInNavigationHistory
import com.intellij.diff.impl.DiffWindowBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFile

abstract class DiffVirtualFileBase(name: String) :
  LightVirtualFile(name, DiffFileType.INSTANCE, ""),
  DiffContentVirtualFile, VirtualFileWithoutContent,
  IdeDocumentHistoryImpl.OptionallyIncluded,
  EditorHistoryManager.OptionallyIncluded {
  private val settings by lazy { DiffSettingsHolder.DiffSettings.getSettings() }

  init {
    useDiffWindowDimensionKey()
    turnOffReopeningWindow()
  }

  override fun isIncludedInDocumentHistory(project: Project): Boolean =
    when (settings.isIncludedInNavigationHistory) {
      IncludeInNavigationHistory.Never -> false
      IncludeInNavigationHistory.Always -> true
      IncludeInNavigationHistory.OnlyIfOpen ->
        FileEditorManager.getInstance(project).isFileOpen(this)
    }

  override fun isPersistedInEditorHistory(): Boolean = false

  override fun isIncludedInEditorHistory(project: Project): Boolean =
    settings.isIncludedInNavigationHistory == IncludeInNavigationHistory.Always

  override fun isWritable(): Boolean = false

  override fun toString(): String = "${javaClass.name}@${Integer.toHexString(hashCode())}"

  /**
   * See [DiffEditorEscapeAction]
   */
  open fun createEscapeHandler(): AnAction? {
    return getUserData(ESCAPE_HANDLER)
  }

  companion object {

    fun VirtualFile.useDiffWindowDimensionKey() = putUserData(FileEditorManagerKeys.WINDOW_DIMENSION_KEY, DiffWindowBase.DEFAULT_DIALOG_GROUP_KEY)
    fun VirtualFile.turnOffReopeningWindow() = putUserData(FileEditorManagerKeys.REOPEN_WINDOW, false)

    /**
     * @see [DiffEditorEscapeAction]
     */
    @JvmField
    val ESCAPE_HANDLER = Key<AnAction?>("ESCAPE_HANDLER")
  }
}
