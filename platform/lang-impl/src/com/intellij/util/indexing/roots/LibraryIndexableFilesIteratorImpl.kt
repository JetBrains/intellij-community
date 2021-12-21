// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.impl.LightFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.LibraryOriginImpl
import org.jetbrains.annotations.Nls

class LibraryIndexableFilesIteratorImpl(val libraryName: @NlsSafe String?,
                                        val presentableLibraryName: @Nls String,
                                        val classRootUrls: List<VirtualFilePointer>,
                                        val sourceRootUrls: List<VirtualFilePointer>
) : LibraryIndexableFilesIterator {

  constructor(library: Library) : this(library.name, library.presentableName,
                                       collectPointers(library, OrderRootType.CLASSES),
                                       collectPointers(library, OrderRootType.SOURCES))

  override fun getDebugName() = "Library ${presentableLibraryName}"

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

  override fun getRootUrls(): Set<String> {
    return (classRootUrls + sourceRootUrls).map { it.url }.toSet()
  }

  companion object {
    private fun collectPointers(library: Library, rootType: OrderRootType) = library.rootProvider.getFiles(rootType).map {
      LightFilePointer(it)
    }
  }
}