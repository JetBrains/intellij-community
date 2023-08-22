// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.openapi.vfs.VirtualFile

/**
 * This interface is deprecated, override [BaseProjectDirectories] service instead.
 */
@Deprecated("Override BaseProjectDirectories service instead")
interface BaseDirectorySuggester {
  /**
   * Return a directory which can be considered as the main directory for [project] or `null` if this implementation cannot suggest anything
   * special for [project]. The result will be used by [Project.guessProjectDir]
   * in various actions to suggest a directory for the project.
   */
  fun suggestBaseDirectory(project: Project): VirtualFile?
}
