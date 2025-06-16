// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.SdkOriginImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*

@ApiStatus.Internal
class SdkIndexableFilesIteratorImpl private constructor(private val sdkName: String,
                                                        private val sdkType: SdkTypeId?,
                                                        private val sdkHome: String?,
                                                        private val rootsToIndex: Collection<VirtualFile>) : IndexableFilesIterator {

  override fun getDebugName(): String = "$sdkPresentableName ${sdkName} ${sdkHome} (${rootsToIndex.joinToString { it.name }})"

  private val sdkPresentableName: String
    get() = (sdkType as? SdkType)?.presentableName.takeUnless { it.isNullOrEmpty() }
            ?: IndexingBundle.message("indexable.files.provider.indexing.sdk.unnamed")

  override fun getIndexingProgressText(): @Nls String = IndexingBundle.message("indexable.files.provider.indexing.sdk", sdkPresentableName, sdkName)

  override fun getRootsScanningProgressText(): @Nls String = IndexingBundle.message("indexable.files.provider.scanning.sdk", sdkPresentableName,
                                                                                    sdkName)

  override fun getOrigin(): IndexableSetOrigin = SdkOriginImpl(rootsToIndex)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    return IndexableFilesIterationMethods.iterateRoots(project, rootsToIndex, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return rootsToIndex.map { it.url }.toSet()
  }

  companion object {
    fun createIterator(sdk: Sdk, rootsToIndex: Collection<VirtualFile> = emptyList()): IndexableFilesIterator {
      val roots = rootsToIndex.ifEmpty { getRootsToIndex(sdk) }
      return SdkIndexableFilesIteratorImpl(sdk.name, sdk.sdkType, sdk.homePath, roots)
    }

    fun createIterator(sdkName: String, sdkType: SdkType?, sdkHome: String?, roots: Collection<VirtualFile>): IndexableFilesIterator {
      return SdkIndexableFilesIteratorImpl(sdkName, sdkType, sdkHome, roots)
    }

    private fun getRootsToIndex(sdk: Sdk): Collection<VirtualFile> {
      val rootProvider = sdk.rootProvider
      return rootProvider.getFiles(OrderRootType.SOURCES).toList() + rootProvider.getFiles(OrderRootType.CLASSES)
    }

    fun createIterators(sdk: Sdk, listOfRootsToFilter: List<VirtualFile>): Collection<IndexableFilesIterator> {
      val sdkRoots = getRootsToIndex(sdk).toMutableList()
      val rootsToIndex = filterRootsToIterate(sdkRoots, listOfRootsToFilter)

      return if (rootsToIndex.isEmpty()) {
        emptyList()
      }
      else {
        Collections.singletonList(SdkIndexableFilesIteratorImpl(sdk.name, sdk.sdkType, sdk.homePath,rootsToIndex))
      }
    }

    private fun filterRootsToIterate(initialRoots: MutableList<VirtualFile>,
                                     listOfRootsToFilter: List<VirtualFile>): List<VirtualFile> {
      val rootsToFilter = listOfRootsToFilter.toMutableList()
      val rootsToIndex = mutableListOf<VirtualFile>()

      val iteratorToFilter = rootsToFilter.iterator()
      while (iteratorToFilter.hasNext()) {
        val next = iteratorToFilter.next()
        for (sdkRoot in initialRoots) {
          if (VfsUtil.isAncestor(next, sdkRoot, false)) {
            rootsToIndex.add(sdkRoot)
            initialRoots.remove(sdkRoot)
            iteratorToFilter.remove()
            break
          }
        }
      }
      for (file in rootsToFilter) {
        for (sdkRoot in initialRoots) {
          if (VfsUtil.isAncestor(sdkRoot, file, false)) {
            rootsToIndex.add(file)
          }
        }
      }
      return rootsToIndex
    }
  }
}