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
import com.intellij.util.containers.TreeNodeProcessingResult
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
    val rootsSet = roots.toSet()
    val finalFileFilter = fileFilter.and { shouldIndexFile(it, projectFileIndex, rootsSet, excludeNonProjectRoots) }
    return roots.all { root ->
      VfsUtilCore.iterateChildrenRecursively(root, finalFileFilter, contentIterator)
    }
  }

  private fun shouldIndexFile(
    file: VirtualFile,
    projectFileIndex: ProjectFileIndex,
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
      return shouldIndexFile(targetFile, projectFileIndex, rootsSet, excludeNonProjectRoots)
    }
    if (file !is VirtualFileWithId || file.id <= 0) {
      return false
    }
    if (excludeNonProjectRoots && runReadAction { projectFileIndex.isExcluded(file) }) {
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
    val fileTypeRegistry = FileTypeRegistry.getInstance()
    if (!iterateAsContent) {
      val rootsSet = roots.toSet()
      val finalFileFilter = fileFilter.and {
        !fileTypeRegistry.isFileIgnored(it) || roots.contains(it)
      }.and {
        shouldIndexFileIndependentlyFromProjectFileIndex(it, rootsSet, exclusionCondition)
      }
      return roots.all { root ->
        VfsUtilCore.iterateChildrenRecursively(root, finalFileFilter, contentIterator)
      }
    }
    val contentIteratorEx = toContentIteratorEx(contentIterator)
    val rootsSet = roots.toSet()
    return roots.all { root ->
      val result = VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
        override fun visitFileEx(file: VirtualFile): Result {
          if (exclusionCondition.test(file)) {
            return SKIP_CHILDREN
          }
          if (fileTypeRegistry.isFileIgnored(file) && !roots.contains(file)) {
            return SKIP_CHILDREN
          }
          val accepted = fileFilter.accept(file) &&
                         shouldIndexFileIndependentlyFromProjectFileIndex(file, rootsSet, null)
          val status = if (accepted) contentIteratorEx.processFileEx(file) else TreeNodeProcessingResult.CONTINUE
          return when (status) {
            TreeNodeProcessingResult.CONTINUE -> CONTINUE
            TreeNodeProcessingResult.SKIP_CHILDREN -> SKIP_CHILDREN
            TreeNodeProcessingResult.SKIP_TO_PARENT -> skipTo(file.parent)
            TreeNodeProcessingResult.STOP -> skipTo(root)
          }
        }
      })
      return@all !Comparing.equal<VirtualFile>(result.skipToParent, root)
    }
  }

  private fun toContentIteratorEx(processor: ContentIterator): ContentIteratorEx {
    return if (processor is ContentIteratorEx) {
      processor
    }
    else ContentIteratorEx { fileOrDir: VirtualFile? ->
      if (processor.processFile(fileOrDir!!)) TreeNodeProcessingResult.CONTINUE else TreeNodeProcessingResult.STOP
    }
  }

  private fun shouldIndexFileIndependentlyFromProjectFileIndex(
    file: VirtualFile,
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
      return shouldIndexFileIndependentlyFromProjectFileIndex(targetFile, rootsSet, exclusionCondition)
    }
    if (file !is VirtualFileWithId || file.id <= 0) {
      return false
    }
    if (exclusionCondition?.test(file) == true) {
      return false
    }
    return true
  }
}