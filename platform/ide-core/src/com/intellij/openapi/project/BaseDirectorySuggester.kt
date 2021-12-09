// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.openapi.vfs.VirtualFile

/**
 * Implement this interface to provide a custom way to suggest base directory for projects. The implementation should be registered in your `plugin.xml`:
 * ```
 * <extensions defaultExtensionNs="com.intellij">
 *   <baseDirectorySuggester implementation="qualified-class-name"/>
 * </extensions>
 * ```
 */
interface BaseDirectorySuggester {
  /**
   * Return a directory which can be considered as the main directory for [project] or `null` if this implementation cannot suggest anything
   * special for [project]. The result will be used by [Project.guessProjectDir]
   * in various actions to suggest a directory for the project.
   */
  fun suggestBaseDirectory(project: Project): VirtualFile?
}
