// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IndexableFilesIterationMethods {

  private val followSymlinks
    get() = Registry.`is`("indexer.follows.symlinks")

  /**
   *  @param excludeNonProjectRoots - if only files considered project content by [ProjectFileIndex] should be iterated
   */
  fun iterateRoots(
    project: Project,
    roots: Iterable<VirtualFile>,
    contentIterator: ContentIterator,
    fileFilter: VirtualFileFilter,
    excludeNonProjectRoots: Boolean = true
  ): Boolean {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    val rootsSet = roots.toSet()
    val finalFileFilter = fileFilter.and { shouldIndexFile(it, projectFileIndex, rootsSet, excludeNonProjectRoots) }
    return roots.all { root ->
      VfsUtilCore.iterateChildrenRecursively(root, finalFileFilter, contentIterator)
    }
  }

  @JvmOverloads
  fun shouldIndexFile(
    file: VirtualFile,
    projectFileIndex: ProjectFileIndex,
    rootsSet: Set<VirtualFile>,
    excludeNonProjectRoots: Boolean = true
  ): Boolean {
    if (file.`is`(VFileProperty.SYMLINK)) {
      if (!followSymlinks) {
        return false
      }
      val targetFile = file.canonicalFile
      if (targetFile == null || targetFile.`is`(VFileProperty.SYMLINK)) {
        // Broken or recursive symlink. The second check should not happen but let's guarantee no StackOverflowError.
        return false
      }
      if (file in rootsSet) {
        return true
      }
      return shouldIndexFile(targetFile, projectFileIndex, rootsSet, excludeNonProjectRoots)
    }
    if (file !is VirtualFileWithId || file.id <= 0) {
      return false
    }
    return !excludeNonProjectRoots || runReadAction { !projectFileIndex.isExcluded(file) }
  }
}