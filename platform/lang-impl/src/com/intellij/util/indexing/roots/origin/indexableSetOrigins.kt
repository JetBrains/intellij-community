// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.kind.*

internal data class ModuleRootOriginImpl(override val module: Module,
                                         override val roots: List<VirtualFile>) : ModuleRootOrigin

internal data class LibraryOriginImpl(override val classRootUrls: List<VirtualFilePointer>,
                                      override val sourceRootUrls: List<VirtualFilePointer>) : LibraryOrigin

internal data class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary) : SyntheticLibraryOrigin

internal data class SdkOriginImpl(override val sdk: Sdk) : SdkOrigin

internal data class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor) : IndexableSetContributorOrigin

internal data class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin