// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * A cache for scheduling and loading highlighting passes for a set of files in the background, doesn't require opened editor.
 */
interface HighlightingPassesCache {
  /**
   * Schedules a list of VirtualFile for the loading highlighting passes in the background without the editor
   *
   * @param files Highlighting passes are loaded for the files' list
   * @param sourceOnly Boolean indicating whether the files are source files or not
   */
  fun schedule(files: List<VirtualFile>, sourceOnly: Boolean = true)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HighlightingPassesCache = project.getService(HighlightingPassesCache::class.java)
  }
}