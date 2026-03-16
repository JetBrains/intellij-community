// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.GenericContentEntityOriginImpl
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.SdkOriginImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Unmodifiable

@ApiStatus.Internal
class GenericDependencyIterator(
  private val origin: IndexableSetOrigin,
  private val root: VirtualFile,
  private val indexingProgressText: @NlsContexts.ProgressText String,
  private val rootsScanningProgressText: @NlsContexts.ProgressText String,
  private val debugName: String,
  private val recursive: Boolean = true,
) : IndexableFilesIterator {
  override fun getDebugName(): @NonNls String {
    return debugName
  }

  override fun getIndexingProgressText(): @NlsContexts.ProgressText String {
    return indexingProgressText
  }

  override fun getRootsScanningProgressText(): @NlsContexts.ProgressText String {
    return rootsScanningProgressText
  }

  override fun getOrigin(): IndexableSetOrigin {
    return origin
  }

  override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
    return if (recursive) {
      IndexableFilesIterationMethods.iterateRoots(project, listOf(root), fileIterator, fileFilter)
    } else {
      IndexableFilesIterationMethods.iterateRootsNonRecursively(project, listOf(root), fileIterator, fileFilter)
    }
  }

  override fun getRootUrls(project: Project): @Unmodifiable Set<String> {
    return setOf(root.url)
  }

  companion object {
    fun forContentRoot(entityPointer: EntityPointer<*>, recursive: Boolean, root: VirtualFile,): IndexableFilesIterator {
      return GenericDependencyIterator(
        // TOOD remove IndexingRootHolder
        origin = GenericContentEntityOriginImpl(entityPointer, IndexingRootHolder.fromFile(root)),
        root = root,
        indexingProgressText = IndexingBundle.message("indexable.files.provider.indexing.content"),
        rootsScanningProgressText = IndexingBundle.message("indexable.files.provider.scanning.content"),
        debugName = "Module unaware content roots from entity (" + root.name + ")",
        recursive = recursive
      )
    }

    fun forLibraryEntity(origin: IndexableSetOrigin, libraryName: String, root: VirtualFile, sourceRoot: Boolean): IndexableFilesIterator {
      val debugMessage = if (sourceRoot) {
        "(source root ${root.name})"
      }
      else {
        "(class root ${root.name})"
      }
      return GenericDependencyIterator(origin, root,
                                       indexingProgressText = IndexingBundle.message("indexable.files.provider.indexing.library.name", libraryName),
                                       rootsScanningProgressText = IndexingBundle.message("indexable.files.provider.scanning.library.name", libraryName),
                                       debugName = "Library ${libraryName} $debugMessage")
    }

    fun forSdkEntity(sdkName: String, sdkType: SdkTypeId?, sdkHome: String?, root: VirtualFile): IndexableFilesIterator {
      val sdkPresentableName: String = (sdkType as? SdkType)?.presentableName.takeUnless { it.isNullOrEmpty() } ?: IndexingBundle.message("indexable.files.provider.indexing.sdk.unnamed")

      return GenericDependencyIterator(SdkOriginImpl(listOf(root)),
                                       root,
                                      indexingProgressText = IndexingBundle.message("indexable.files.provider.indexing.sdk", sdkPresentableName, sdkName),
                                      rootsScanningProgressText = IndexingBundle.message("indexable.files.provider.scanning.sdk", sdkPresentableName, sdkName),
                                      debugName = "$sdkPresentableName ${sdkName} ${sdkHome} (${root.name})")
    }
  }
}