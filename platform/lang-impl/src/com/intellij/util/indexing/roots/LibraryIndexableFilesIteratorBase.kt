// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle

abstract class LibraryIndexableFilesIteratorBase(val library: Library) : LibraryIndexableFilesIterator {
  override fun getDebugName() = "Library ${library.presentableName}"

  override fun getIndexingProgressText(): String = IndexingBundle.message("indexable.files.provider.indexing.library.name",
    library.presentableName)

  override fun getRootsScanningProgressText(): String {
    val libraryName = library.name
    if (!libraryName.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.library.name", libraryName)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val roots = getRoots()
    return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter)
  }

  fun getRoots() = runReadAction {
    if ((library as LibraryEx).isDisposed) {
      listOf<VirtualFile>()
    }
    else {
      val rootProvider = library.rootProvider
      rootProvider.getFiles(OrderRootType.SOURCES).toList() + rootProvider.getFiles(OrderRootType.CLASSES)
    }
  }

  override fun getRootUrls(): Set<String> {
    val rootProvider = library.rootProvider
    return (rootProvider.getUrls(OrderRootType.SOURCES) + rootProvider.getUrls(OrderRootType.CLASSES)).toSet()
  }
}