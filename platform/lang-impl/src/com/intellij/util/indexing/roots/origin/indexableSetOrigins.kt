// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.kind.*

data class ModuleRootOriginImpl(override val module: Module,
                           override val roots: List<VirtualFile>) : ModuleRootOrigin

data class LibraryOriginImpl(override val libraryName: String?,
                             override val classRootUrls: List<VirtualFilePointer>,
                             override val sourceRootUrls: List<VirtualFilePointer>) : LibraryOrigin

data class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary) : SyntheticLibraryOrigin

data class SdkOriginImpl(override val sdk: Sdk) : SdkOrigin

data class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor) : IndexableSetContributorOrigin

data class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin