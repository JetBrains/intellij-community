// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.IndexableFilesIterationMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.SdkIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.kind.*
import org.jetbrains.annotations.Nls

internal data class ModuleRootOriginImpl(override val module: Module,
                                         override val roots: List<VirtualFile>) : ModuleRootOrigin

internal class ModuleRootIterableOriginImpl(override val module: Module,
                                            override val roots: List<VirtualFile>,
                                            excludedRoots: Collection<VirtualFile>) : ModuleRootOrigin,
                                                                                      IndexableSetIterableOriginBase() {
  override val iterationRoots: Collection<VirtualFile>
    get() = roots
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  init {
    exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedRoots)
  }


  override fun getDebugName(): String {
    val rootsDebugStr = if (roots.isEmpty()) "empty" else roots.map { it.name }.sorted().joinToString(", ", limit = 10)
    return "Module '" + module.name + "' ($rootsDebugStr)"
  }

  override fun getIndexingProgressText(): @NlsContexts.ProgressText String =
    if (ModuleType.isInternal(module)) {
      IndexingBundle.message("indexable.files.provider.indexing.internal.module.name")
    }
    else {
      IndexingBundle.message("indexable.files.provider.indexing.module.name", module.name)
    }

  override fun getRootsScanningProgressText(): @NlsContexts.ProgressText String {
    if (ModuleType.isInternal(module))
      return IndexingBundle.message("indexable.files.provider.scanning.internal.module.name")
    return IndexingBundle.message("indexable.files.provider.scanning.module.name", module.name)
  }

  fun copyWithAdditionalExcludedFiles(excludedFiles: Set<VirtualFile>): ModuleRootIterableOriginImpl {
    val copy = ModuleRootIterableOriginImpl(module, roots, emptyList())
    copy.exclusionData.load(exclusionData)
    copy.exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedFiles)
    return copy
  }

  fun copyWithChildContentRoots(childContentRoots: Collection<VirtualFile>): ModuleRootIterableOriginImpl {
    val copy = ModuleRootIterableOriginImpl(module, roots, emptyList())
    copy.exclusionData.load(exclusionData)
    copy.exclusionData.setExcludedRootsFromChildContentRoots(childContentRoots)
    return copy
  }

  fun copyWithFilteredRoots(rootsToFilter: Collection<VirtualFile>): ModuleRootIterableOriginImpl? {
    val filteredRootsToIterate = SdkIndexableFilesIteratorImpl.filterRootsToIterate(roots.toMutableList(),
                                                                                    rootsToFilter.toMutableList())
    if (filteredRootsToIterate.isEmpty()) return null
    val copy = ModuleRootIterableOriginImpl(module, filteredRootsToIterate, emptyList())
    copy.exclusionData.load(exclusionData)
    return copy
  }
}

internal data class LibraryOriginImpl(override val classRoots: List<VirtualFile>,
                                      override val sourceRoots: List<VirtualFile>) : LibraryOrigin

internal class LibraryIterableOriginImpl(override val classRoots: List<VirtualFile>,
                                         override val sourceRoots: List<VirtualFile>,
                                         excludedRoots: Collection<VirtualFile>,
                                         private val libraryName: @NlsSafe String?,
                                         private val presentableLibraryName: @Nls String) : LibraryOrigin, IndexableSetIterableOriginBase() {
  override val iterationRoots: Collection<VirtualFile>
    get() = classRoots + sourceRoots
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  init {
    exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedRoots)
  }

  override fun getDebugName() = "Library ${presentableLibraryName} " +
                                "(#${classRoots.validCount()} class roots, " +
                                "#${sourceRoots.validCount()} source roots)"

  override fun getIndexingProgressText(): String = IndexingBundle.message("indexable.files.provider.indexing.library.name",
                                                                          presentableLibraryName)

  override fun getRootsScanningProgressText(): String {
    if (!libraryName.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.library.name", libraryName)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  private fun List<VirtualFile>.validCount(): Int = filter { it.isValid }.size
}

internal data class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary,
                                               override val rootsToIndex: Collection<VirtualFile>) : SyntheticLibraryOrigin

internal class SyntheticLibraryIterableOriginImpl(override val syntheticLibrary: SyntheticLibrary,
                                                  override val rootsToIndex: Collection<VirtualFile>,
                                                  excludedRoots: Collection<VirtualFile>,
                                                  excludeCondition: Condition<VirtualFile>?,
                                                  private val name: String?) : SyntheticLibraryOrigin,
                                                                               IndexableSetIterableOriginBase() {

  override val iterationRoots: Collection<VirtualFile>
    get() = rootsToIndex
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  init {
    exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedRoots)
    exclusionData.addExcludedFileCondition(excludeCondition)
  }

  override fun getDebugName() = name.takeUnless { it.isNullOrEmpty() }?.let { "Synthetic library '$it'" }
                                ?: syntheticLibrary.toString()

  override fun getIndexingProgressText(): String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.indexing.named.provider", name)
    }
    return IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies")
  }

  override fun getRootsScanningProgressText(): String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.library.name", name)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }
}

internal data class SdkOriginImpl(override val sdk: Sdk,
                                  override val rootsToIndex: Collection<VirtualFile>) : SdkOrigin

internal class SdkIterableOriginImpl(override val sdk: Sdk,
                                     override val rootsToIndex: Collection<VirtualFile>) : SdkOrigin,
                                                                                           IndexableSetIterableOriginBase() {
  override val iterationRoots: Collection<VirtualFile>
    get() = rootsToIndex
  override val exclusionData: ExclusionData = ExclusionData.createExclusionData(iterationRoots)

  override fun getDebugName() = "$sdkPresentableName ${sdk.name} ${rootsToIndex.joinToString { it.path }}"

  private val sdkPresentableName: String
    get() = (sdk.sdkType as? SdkType)?.presentableName.takeUnless { it.isNullOrEmpty() }
            ?: IndexingBundle.message("indexable.files.provider.indexing.sdk.unnamed")

  override fun getIndexingProgressText() = IndexingBundle.message("indexable.files.provider.indexing.sdk", sdkPresentableName, sdk.name)

  override fun getRootsScanningProgressText() = IndexingBundle.message("indexable.files.provider.scanning.sdk", sdkPresentableName,
                                                                       sdk.name)

  fun copyWithAdditionalExcludedFiles(excludedFiles: Collection<VirtualFile>): IndexableSetIterableOrigin {
    val copy = SdkIterableOriginImpl(sdk, rootsToIndex)
    copy.exclusionData.load(exclusionData)
    copy.exclusionData.addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(excludedFiles)
    return copy
  }

  fun copyWithFilteredRoots(rootsToFilter: Collection<VirtualFile>): SdkIterableOriginImpl? {
    val filteredRootsToIterate = SdkIndexableFilesIteratorImpl.filterRootsToIterate(rootsToIndex.toMutableList(),
                                                                                    rootsToFilter.toMutableList())
    if (filteredRootsToIterate.isEmpty()) return null
    val copy = SdkIterableOriginImpl(sdk, filteredRootsToIterate)
    copy.exclusionData.load(exclusionData)
    return copy
  }
}

internal data class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor,
                                                      override val rootsToIndex: Set<VirtualFile>) : IndexableSetContributorOrigin

internal class IndexableSetContributorIterableOriginImpl(private val name: String?,
                                                         private val debugName: String,
                                                         private val projectAware: Boolean,
                                                         override val indexableSetContributor: IndexableSetContributor,
                                                         override val rootsToIndex: Set<VirtualFile>) :
  IndexableSetContributorOrigin, IndexableSetIterableOriginBase() {
  override val iterationRoots: Collection<VirtualFile>
    get() = rootsToIndex
  override val exclusionData: ExclusionData = ExclusionData.getDummyExclusionData()

  override fun getDebugName(): String {
    return "Indexable set contributor '$debugName' ${if (projectAware) "(project)" else "(non-project)"}"
  }

  override fun getIndexingProgressText(): @NlsContexts.ProgressText String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.indexing.named.provider", name)
    }
    return IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies")
  }

  override fun getRootsScanningProgressText(): @NlsContexts.ProgressText String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.files.contributor", name)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }
}

internal data class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin

abstract class IndexableSetIterableOriginBase : IndexableSetIterableOrigin() {

  abstract fun getDebugName(): String

  abstract fun getIndexingProgressText(): @NlsContexts.ProgressText String

  abstract fun getRootsScanningProgressText(): @NlsContexts.ProgressText String

  override fun createIterator(): IndexableFilesIterator = MyIterator()

  inner class MyIterator : IndexableFilesIterator {

    override fun getDebugName(): String {
      return this@IndexableSetIterableOriginBase.getDebugName()
    }

    override fun getIndexingProgressText(): @NlsContexts.ProgressText String {
      return this@IndexableSetIterableOriginBase.getIndexingProgressText()
    }

    override fun getRootsScanningProgressText(): @NlsContexts.ProgressText String {
      return this@IndexableSetIterableOriginBase.getRootsScanningProgressText()
    }

    override fun getOrigin(): IndexableSetOrigin {
      return this@IndexableSetIterableOriginBase
    }

    override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
      return IndexableFilesIterationMethods.iterateRootsIndependentlyFromProjectFileIndex(getRoots(), fileIterator, fileFilter,
                                                                                          exclusionData, origin is ModuleRootOrigin)
    }

    override fun getRootUrls(project: Project): Set<String> {
      return getRoots().map { root -> root.url }.toSet()
    }

    private fun getRoots(): Collection<VirtualFile> = ReadAction.nonBlocking<Collection<VirtualFile>> {
      return@nonBlocking iterationRoots.filter { it.isValid }
    }.executeSynchronously()
  }
}