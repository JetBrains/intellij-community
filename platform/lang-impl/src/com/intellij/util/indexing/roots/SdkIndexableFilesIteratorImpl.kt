// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.SdkOriginImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SdkIndexableFilesIteratorImpl(private val sdk: Sdk) : IndexableFilesIterator {
  override fun getDebugName() = "$sdkPresentableName ${sdk.name}"

  private val sdkPresentableName: String
    get() = (sdk.sdkType as? SdkType)?.presentableName.takeUnless { it.isNullOrEmpty() }
            ?: IndexingBundle.message("indexable.files.provider.indexing.sdk.unnamed")

  override fun getIndexingProgressText() = IndexingBundle.message("indexable.files.provider.indexing.sdk", sdkPresentableName, sdk.name)

  override fun getRootsScanningProgressText() = IndexingBundle.message("indexable.files.provider.scanning.sdk", sdkPresentableName,
                                                                       sdk.name)

  override fun getOrigin(): IndexableSetOrigin = SdkOriginImpl(sdk)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val roots = runReadAction {
      val rootProvider = sdk.rootProvider
      rootProvider.getFiles(OrderRootType.SOURCES).toList() + rootProvider.getFiles(OrderRootType.CLASSES)
    }
    return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    val rootProvider = sdk.rootProvider
    return (rootProvider.getUrls(OrderRootType.SOURCES) + rootProvider.getUrls(OrderRootType.CLASSES)).toSet()
  }
}