// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.LibraryOriginImpl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class LibraryIndexableFilesIteratorImpl
private constructor(private val libraryName: @NlsSafe String?,
                    private val presentableLibraryName: @Nls String,
                    private val classRoots: List<VirtualFile>,
                    private val sourceRoots: List<VirtualFile>) : LibraryIndexableFilesIterator {

  override fun getDebugName(): String {
    return if (Registry.`is`("use.workspace.file.index.to.generate.iterators")) {
      val debugMessage = if (classRoots.isNotEmpty()) {
        "(class root ${classRoots.first().name})"
      } else if (sourceRoots.isNotEmpty()) {
        "(source root ${sourceRoots.first().name})"
      } else {
        "(no root)"
      }

      "Library ${presentableLibraryName} $debugMessage"
    } else {
      "Library ${presentableLibraryName} " +
      "(#${classRoots.validCount()} class roots, " +
      "#${sourceRoots.validCount()} source roots)"
    }
  }

  override fun getIndexingProgressText(): String = IndexingBundle.message("indexable.files.provider.indexing.library.name",
                                                                          presentableLibraryName)

  override fun getRootsScanningProgressText(): String {
    if (!libraryName.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.library.name", libraryName)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun getOrigin(): LibraryOrigin {
    return LibraryOriginImpl(classRoots, sourceRoots)
  }

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val roots = runReadAction {
      getRoots().filter { it.isValid }.toSet()
    }
    return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return getRoots().map { it.url }.toSet()
  }

  private fun getRoots(): Sequence<VirtualFile> {
    return if (iterateOverSourceRoots) (classRoots.asSequence() + sourceRoots.asSequence()) else classRoots.asSequence()
  }

  companion object {
    private val iterateOverSourceRoots = SystemProperties.getBooleanProperty("LibraryIndexableFilesIterator.iterate.over.sources", true)

    fun collectFiles(library: Library, rootType: OrderRootType, rootsToFilter: Collection<VirtualFile>? = null): List<VirtualFile> {
      val libraryRoots = library.rootProvider.getFiles(rootType)
      val rootsToIterate: List<VirtualFile> = rootsToFilter?.filter { root ->
        libraryRoots.find { libraryRoot ->
          VfsUtil.isAncestor(libraryRoot, root, false)
        } != null
      } ?: libraryRoots.toList()
      return rootsToIterate
    }

    @RequiresReadLock
    @JvmStatic
    fun createIterator(library: Library,
                       roots: Collection<VirtualFile>? = null,
                       sourceRoots: Collection<VirtualFile>? = null): LibraryIndexableFilesIteratorImpl? =
      if (library is LibraryEx && library.isDisposed)
        null
      else
        LibraryIndexableFilesIteratorImpl(library.name, library.presentableName,
                                          collectFiles(library, OrderRootType.CLASSES, roots),
                                          collectFiles(library, OrderRootType.SOURCES, sourceRoots))

    @JvmStatic
    fun createIteratorList(library: Library): List<IndexableFilesIterator> =
      createIterator(library)?.run { listOf(this) } ?: emptyList()
  }

  private fun List<VirtualFile>.validCount(): Int = filter { it.isValid }.size
}