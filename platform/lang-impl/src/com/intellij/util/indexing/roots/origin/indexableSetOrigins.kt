// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.kind.*
import com.intellij.workspaceModel.storage.EntityReference

internal data class ModuleRootOriginImpl(override val module: Module,
                                         override val roots: List<VirtualFile>) : ModuleRootOrigin

internal data class LibraryOriginImpl(override val classRoots: List<VirtualFile>,
                                      override val sourceRoots: List<VirtualFile>) : LibraryOrigin

internal data class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary,
                                               override val rootsToIndex: Collection<VirtualFile>) : SyntheticLibraryOrigin

internal data class SdkOriginImpl(override val sdk: Sdk,
                                  override val rootsToIndex: Collection<VirtualFile>) : SdkOrigin

internal data class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor,
                                                      override val rootsToIndex: Set<VirtualFile>) : IndexableSetContributorOrigin

internal data class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin

internal data class ModuleAwareContentEntityOriginImpl(override val module: Module,
                                                       override val reference: EntityReference<*>,
                                                       override val roots: Collection<VirtualFile>) : ModuleAwareContentEntityOrigin

internal data class GenericContentEntityOriginImpl(override val reference: EntityReference<*>,
                                                   override val roots: Collection<VirtualFile>) : GenericContentEntityOrigin

internal data class ExternalEntityOriginImpl(override val reference: EntityReference<*>,
                                             override val roots: Collection<VirtualFile>,
                                             override val sourceRoots: Collection<VirtualFile>) : ExternalEntityOrigin