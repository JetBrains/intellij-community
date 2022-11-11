// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.kind.*

internal data class ModuleRootOriginImpl(override val module: Module,
                                         override val roots: List<VirtualFile>) : ModuleRootOrigin

internal class ModuleRootSelfDependentOriginImpl(override val module: Module,
                                                 override val roots: List<VirtualFile>,
                                                 excludedRoots: Collection<VirtualFile>) : ModuleRootOrigin,
                                                                                           IndexableSetSelfDependentOrigin() {
  override val iterationRoots: Collection<VirtualFile>
    get() = roots
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  init {
    exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedRoots)
  }

  fun copyWithAdditionalExcludedFiles(excludedFiles: Set<VirtualFile>): ModuleRootSelfDependentOriginImpl {
    val copy = ModuleRootSelfDependentOriginImpl(module, roots, emptyList())
    copy.exclusionData.load(exclusionData)
    copy.exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedFiles)
    return copy
  }
}

internal data class LibraryOriginImpl(override val classRoots: List<VirtualFile>,
                                      override val sourceRoots: List<VirtualFile>) : LibraryOrigin

internal class LibrarySelfDependentOriginImpl(override val classRoots: List<VirtualFile>,
                                              override val sourceRoots: List<VirtualFile>,
                                              excludedRoots: Collection<VirtualFile>) : LibraryOrigin, IndexableSetSelfDependentOrigin() {
  override val iterationRoots: Collection<VirtualFile>
    get() = classRoots + sourceRoots
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  init {
    exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedRoots)
  }
}

internal data class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary,
                                               override val rootsToIndex: Collection<VirtualFile>) : SyntheticLibraryOrigin

internal class SyntheticLibrarySelfDependentOriginImpl(override val syntheticLibrary: SyntheticLibrary,
                                                       override val rootsToIndex: Collection<VirtualFile>,
                                                       excludedRoots: Collection<VirtualFile>,
                                                       excludeCondition: Condition<VirtualFile>?) : SyntheticLibraryOrigin,
                                                                                                    IndexableSetSelfDependentOrigin() {

  override val iterationRoots: Collection<VirtualFile>
    get() = rootsToIndex
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  init {
    exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedRoots)
    exclusionData.addExcludedFileCondition(excludeCondition)
  }
}

internal data class SdkOriginImpl(override val sdk: Sdk,
                                  override val rootsToIndex: Collection<VirtualFile>) : SdkOrigin

internal class SdkSelfDependentOriginImpl(override val sdk: Sdk,
                                          override val rootsToIndex: Collection<VirtualFile>) : SdkOrigin,
                                                                                                IndexableSetSelfDependentOrigin() {
  override val iterationRoots: Collection<VirtualFile>
    get() = rootsToIndex
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  fun copyWithAdditionalExcludedFiles(excludedFiles: Collection<VirtualFile>): IndexableSetSelfDependentOrigin {
    val copy = SdkSelfDependentOriginImpl(sdk, rootsToIndex)
    copy.exclusionData.load(exclusionData)
    copy.exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedFiles)
    return copy
  }
}

internal data class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor,
                                                      override val rootsToIndex: Set<VirtualFile>) : IndexableSetContributorOrigin

internal class IndexableSetContributorSelfDependentOriginImpl(override val indexableSetContributor: IndexableSetContributor,
                                                              override val rootsToIndex: Set<VirtualFile>) : IndexableSetContributorOrigin,
                                                                                                             IndexableSetSelfDependentOrigin() {
  override val iterationRoots: Collection<VirtualFile>
    get() = rootsToIndex
  override val exclusionData: ExclusionData = ExclusionData.getDummyExclusionData()
}

internal data class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin