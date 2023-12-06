// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.*
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder
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
    val finalFileFilter = if (project.isDefault) fileFilter else fileFilter.and { shouldIndexFile(it, projectFileIndex, rootsSet, excludeNonProjectRoots) }
    return roots.all { root ->
      VfsUtilCore.iterateChildrenRecursively(root, finalFileFilter, contentIterator)
    }
  }

  private fun iterateRootsNonRecursively(project: Project,
                                         roots: Iterable<VirtualFile>,
                                         contentIterator: ContentIterator,
                                         fileFilter: VirtualFileFilter,
                                         excludeNonProjectRoots: Boolean): Boolean {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    val rootsSet = roots.toSet()
    for (root in roots) {
      if (fileFilter.accept(root) && shouldIndexFile(root, projectFileIndex, rootsSet, excludeNonProjectRoots)) {
        if (!contentIterator.processFile(root)) return false
      }
    }
    return true
  }

  fun iterateRoots(project: Project,
                   roots: IndexingRootHolder,
                   contentIterator: ContentIterator,
                   fileFilter: VirtualFileFilter,
                   excludeNonProjectRoots: Boolean = true
  ): Boolean {
    val recursiveResult = iterateRoots(project, roots.roots, contentIterator, fileFilter, excludeNonProjectRoots)
    if (!recursiveResult) return false
    return iterateRootsNonRecursively(project, roots.nonRecursiveRoots, contentIterator, fileFilter, excludeNonProjectRoots)
  }

  fun iterateRoots(project: Project,
                   rootsHolder: IndexingSourceRootHolder,
                   contentIterator: ContentIterator,
                   fileFilter: VirtualFileFilter,
                   excludeNonProjectRoots: Boolean = true
  ): Boolean {
    val roots = rootsHolder.roots + rootsHolder.sourceRoots
    return iterateRoots(project, roots, contentIterator, fileFilter, excludeNonProjectRoots)
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