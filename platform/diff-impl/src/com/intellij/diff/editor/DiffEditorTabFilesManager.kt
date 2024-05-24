// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

interface DiffEditorTabFilesManager {

  fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean): Array<out FileEditor>

  companion object {
    private const val SHOW_DIFF_IN_EDITOR_SETTING = "show.diff.as.editor.tab"

    /**
     * If enabled - the [DiffViewerVirtualFile] will open like a normal editor in current split.
     * If disabled - it will use [com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode.NEW_WINDOW].
     */
    @JvmStatic
    var isDiffInEditor: Boolean
      get() = AdvancedSettings.getBoolean(SHOW_DIFF_IN_EDITOR_SETTING)
      set(value) = AdvancedSettings.setBoolean(SHOW_DIFF_IN_EDITOR_SETTING, value)

    @JvmStatic
    val isDiffInWindow: Boolean get() = !isDiffInEditor

    @JvmStatic
    fun isDiffOpenedInNewWindow(file: VirtualFile): Boolean = DIFF_OPENED_IN_NEW_WINDOW.get(file, false)

    @JvmStatic
    fun getInstance(project: Project): DiffEditorTabFilesManager = project.service()
  }
}

@JvmField
val DIFF_OPENED_IN_NEW_WINDOW = Key<Boolean>("DIFF_OPENED_IN_NEW_WINDOW")

internal class DefaultDiffTabFilesManager(private val project: Project) : DiffEditorTabFilesManager {

  override fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    return FileEditorManager.getInstance(project).openFile(diffFile, true)
  }
}
