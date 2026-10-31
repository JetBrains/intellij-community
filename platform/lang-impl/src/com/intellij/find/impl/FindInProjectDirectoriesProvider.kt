// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Defines the project of a Find in Files search as a set of directories.
 *
 * The platform searches the project through the indexable file providers and a traversal of the non-indexable files.
 * A product whose project is a set of directories, such as JetBrains Light, gives them here instead. The search then
 * walks them with a [com.intellij.find.DirectorySearchEngine], the way it walks a directory the user named, and a
 * reliable [com.intellij.find.FindInProjectSearchEngine] that covers them skips the walk.
 *
 * The search asks when the model names no directory and no module. The scope of the model still filters every candidate.
 */
@ApiStatus.Internal
interface FindInProjectDirectoriesProvider {
  /** The directories that stand for [project], or null when this provider leaves [project] to the platform search. */
  fun getProjectDirectories(project: Project): List<VirtualFile>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<FindInProjectDirectoriesProvider> = ExtensionPointName.create("com.intellij.findInProjectDirectoriesProvider")

    /** The answer of the first provider that defines [project], or null when none does. */
    @JvmStatic
    fun findProjectDirectories(project: Project): List<VirtualFile>? = EP_NAME.computeSafeIfAny { it.getProjectDirectories(project) }
  }
}
