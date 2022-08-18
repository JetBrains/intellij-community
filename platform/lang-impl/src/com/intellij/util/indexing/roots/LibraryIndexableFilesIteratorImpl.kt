// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.impl.LightFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.LibraryOriginImpl
import org.jetbrains.annotations.Nls

class LibraryIndexableFilesIteratorImpl
private constructor(private val libraryName: @NlsSafe String?,
                    private val presentableLibraryName: @Nls String,
                    private val classRootUrls: List<VirtualFilePointer>,
                    private val sourceRootUrls: List<VirtualFilePointer>) : LibraryIndexableFilesIterator {

  override fun getDebugName() = "Library ${presentableLibraryName} " +
                                "(#${classRootUrls.validCount()} class roots, " +
                                "#${sourceRootUrls.validCount()} source roots)"

  override fun getIndexingProgressText(): String = IndexingBundle.message("indexable.files.provider.indexing.library.name",
                                                                          presentableLibraryName)

  override fun getRootsScanningProgressText(): String {
    if (!libraryName.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.library.name", libraryName)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun getOrigin(): LibraryOrigin {
    return LibraryOriginImpl(classRootUrls, sourceRootUrls)
  }

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val roots = runReadAction {
      (classRootUrls.asSequence() + sourceRootUrls.asSequence()).mapNotNull { it.file }.toSet()
    }
    return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return (classRootUrls + sourceRootUrls).map { it.url }.toSet()
  }

  companion object {
    private fun collectPointers(library: Library, rootType: OrderRootType, rootsToFilter: List<VirtualFile>? = null): List<LightFilePointer> {
      val libraryRoots = library.rootProvider.getFiles(rootType)
      val rootsToIterate: List<VirtualFile> = rootsToFilter?.filter { root ->
        libraryRoots.find { libraryRoot ->
          VfsUtil.isAncestor(libraryRoot, root, false)
        } != null
      } ?: libraryRoots.toList()
      return rootsToIterate.map { LightFilePointer(it) }
    }

    @RequiresReadLock
    @JvmStatic
    fun createIterator(library: Library, roots: List<VirtualFile>? = null): LibraryIndexableFilesIteratorImpl? =
      if (library is LibraryEx && library.isDisposed)
        null
      else
        LibraryIndexableFilesIteratorImpl(library.name, library.presentableName,
                                          collectPointers(library, OrderRootType.CLASSES, roots),
                                          collectPointers(library, OrderRootType.SOURCES, roots))

    @JvmStatic
    fun createIteratorList(library: Library): List<IndexableFilesIterator> =
      createIterator(library)?.run { listOf(this) } ?: emptyList()
  }

  private fun List<VirtualFilePointer>.validCount(): Int = filter { it.isValid }.size
}