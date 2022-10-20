// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.*
import com.intellij.util.indexing.IndexableFilesFilter
import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

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
    val filters = IndexableFilesFilter.EP_NAME.extensionList
    val rootsSet = roots.toSet()
    val finalFileFilter = fileFilter.and { shouldIndexFile(it, projectFileIndex, filters, rootsSet, excludeNonProjectRoots) }
    return roots.all { root ->
      VfsUtilCore.iterateChildrenRecursively(root, finalFileFilter, contentIterator)
    }
  }

  private fun shouldIndexFile(
    file: VirtualFile,
    projectFileIndex: ProjectFileIndex,
    filters: List<IndexableFilesFilter>,
    rootsSet: Set<VirtualFile>,
    excludeNonProjectRoots: Boolean
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
      return shouldIndexFile(targetFile, projectFileIndex, filters, rootsSet, excludeNonProjectRoots)
    }
    if (file !is VirtualFileWithId || file.id <= 0) {
      return false
    }
    if (excludeNonProjectRoots && runReadAction { projectFileIndex.isExcluded(file) }) {
      return false
    }
    if (filters.isNotEmpty() && filters.none { it.shouldIndex(file) }) {
      return false
    }
    return true
  }

  fun iterateRootsIndependentlyFromProjectFileIndex(
    roots: Iterable<VirtualFile>,
    contentIterator: ContentIterator,
    fileFilter: VirtualFileFilter,
    exclusionCondition: Predicate<VirtualFile>,
    iterateAsContent: Boolean,
  ): Boolean {
    if (!iterateAsContent) {
      val filters = IndexableFilesFilter.EP_NAME.extensionList
      val rootsSet = roots.toSet()
      val finalFileFilter = fileFilter.and { shouldIndexFileIndependentlyFromProjectFileIndex(it, filters, rootsSet, exclusionCondition) }
      return roots.all { root ->
        VfsUtilCore.iterateChildrenRecursively(root, finalFileFilter, contentIterator)
      }
    }//todo[lene] fix this iteration peculiarity
    val fileTypeRegistry = FileTypeRegistry.getInstance()
    val contentIteratorEx = toContentIteratorEx(contentIterator)
    val filters = IndexableFilesFilter.EP_NAME.extensionList
    val rootsSet = roots.toSet()
    return roots.all { root ->
      val result = VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
        override fun visitFileEx(file: VirtualFile): Result {
          if (exclusionCondition.test(file)) {
            return SKIP_CHILDREN
          }
          if (fileTypeRegistry.isFileIgnored(file)) {
            return SKIP_CHILDREN
          }
          val accepted = fileFilter.accept(file) &&
                         shouldIndexFileIndependentlyFromProjectFileIndex(file, filters, rootsSet, null)
          val status = if (accepted) contentIteratorEx.processFileEx(file) else ContentIteratorEx.Status.CONTINUE
          if (status == ContentIteratorEx.Status.CONTINUE) {
            return CONTINUE
          }
          return if (status == ContentIteratorEx.Status.SKIP_CHILDREN) {
            SKIP_CHILDREN
          }
          else skipTo(root)
        }
      })
      return !Comparing.equal<VirtualFile>(result.skipToParent, root)
    }
  }

  private fun toContentIteratorEx(processor: ContentIterator): ContentIteratorEx {
    return if (processor is ContentIteratorEx) {
      processor
    }
    else ContentIteratorEx { fileOrDir: VirtualFile? ->
      if (processor.processFile(fileOrDir!!)) ContentIteratorEx.Status.CONTINUE else ContentIteratorEx.Status.STOP
    }
  }

  private fun shouldIndexFileIndependentlyFromProjectFileIndex(
    file: VirtualFile,
    filters: List<IndexableFilesFilter>,
    rootsSet: Set<VirtualFile>,
    exclusionCondition: Predicate<VirtualFile>?
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
        //valid symlink from included source should be handled
        return true
      }
      return shouldIndexFileIndependentlyFromProjectFileIndex(targetFile, filters, rootsSet, exclusionCondition)
    }
    if (file !is VirtualFileWithId || file.id <= 0) {
      return false
    }
    if (exclusionCondition?.test(file) == true) {
      return false
    }
    if (filters.isNotEmpty() && filters.none { it.shouldIndex(file) }) {
      return false
    }
    return true
  }
}