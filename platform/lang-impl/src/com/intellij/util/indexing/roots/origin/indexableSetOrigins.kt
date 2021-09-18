// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.kind.*
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import java.util.*

data class ModuleRootOriginImpl(override val module: Module,
                           override val roots: List<VirtualFile>) : ModuleRootOrigin

/**
 * Used only with [com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl], when
 * [com.intellij.util.indexing.roots.DefaultProjectIndexableFilesContributor.Companion.indexProjectBasedOnIndexableEntityProviders] == false
 */
@Deprecated("Don't use it in new code", ReplaceWith("LibraryIdOriginImpl"))
class LibraryOriginImpl(override val library: Library, val roots: List<VirtualFile>) : LibraryOrigin {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    @Suppress("DEPRECATION")
    return roots == (other as LibraryOriginImpl).roots
  }

  override fun hashCode(): Int = Objects.hash(roots)
}

class LibraryIdOriginImpl(override val library: Library, private val libraryId: LibraryId) : LibraryOrigin {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    return libraryId == (other as LibraryIdOriginImpl).libraryId
  }

  override fun hashCode(): Int = Objects.hash(libraryId)
}

data class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary) : SyntheticLibraryOrigin

data class SdkOriginImpl(override val sdk: Sdk) : SdkOrigin

data class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor) : IndexableSetContributorOrigin

data class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin