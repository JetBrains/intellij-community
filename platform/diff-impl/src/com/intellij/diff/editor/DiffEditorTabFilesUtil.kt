// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DiffEditorTabFilesUtil {
  @JvmStatic
  val isDiffInWindow: Boolean get() = !isDiffInEditor

  const val SHOW_DIFF_IN_EDITOR_SETTING: String = "show.diff.as.editor.tab"

  /**
   * If enabled - the [DiffViewerVirtualFile] will open like a normal editor in current split.
   * If disabled - it will use [com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode.NEW_WINDOW].
   */
  @JvmStatic
  var isDiffInEditor: Boolean
    get() = AdvancedSettings.getBoolean(SHOW_DIFF_IN_EDITOR_SETTING)
    set(value) {
      if (AdvancedSettings.getBoolean(SHOW_DIFF_IN_EDITOR_SETTING) != value) {
        AdvancedSettings.setBoolean(SHOW_DIFF_IN_EDITOR_SETTING, value)
      }
    }

  private val FORCE_OPENING_IN_NEW_WINDOW: Key<Boolean> = Key.create("Diff.ForceOpeningInNewWindow")

  internal fun setForceOpeningsInNewWindow(file: VirtualFile, value: Boolean?) {
    file.putUserData(FORCE_OPENING_IN_NEW_WINDOW, value)
  }

  fun isForceOpeningsInNewWindow(file: VirtualFile): Boolean {
    return file.getUserData(FORCE_OPENING_IN_NEW_WINDOW) == true
  }

  @JvmStatic
  fun forceOpenInNewWindow(project: Project, file: VirtualFile, requestFocus: Boolean) {
    setForceOpeningsInNewWindow(file, true)
    DiffEditorTabFilesManager.getInstance(project).showDiffFile(file, requestFocus, false)
  }
}
