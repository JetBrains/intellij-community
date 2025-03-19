// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import com.intellij.util.indexing.roots.origin.ModuleRootOriginImpl

/**
 * @param rootHolder is null when iterator should just iterate all files in [module], no explicit root list
 */
internal class ModuleIndexableFilesIteratorImpl private constructor(private val module: Module,
                                                                    rootHolder: IndexingRootHolder?,
                                                                    private val printRootsInDebugName: Boolean) : ModuleIndexableFilesIterator {
  init {
    assert(rootHolder?.isEmpty() != true)
  }

  private val roots = rootHolder?.roots?.let { selectRootVirtualFiles(it) }
  private val nonRecursiveRoots = rootHolder?.nonRecursiveRoots?.toList()


  companion object {

    fun createIterators(module: Module, urlRoots: IndexingUrlRootHolder): Collection<IndexableFilesIterator> {
      val roots = urlRoots.toRootHolder()
      if (roots.isEmpty()) return emptyList()
      // 100 is a totally magic constant here, designed to help Rider to avoid indexing all non-recursive roots with one iterator,
      // which results in indexing on a single thread
      if (roots.size() > 100) {
        return roots.split(100).map { rootSublist -> ModuleIndexableFilesIteratorImpl(module, rootSublist, true) }
      }
      return setOf(ModuleIndexableFilesIteratorImpl(module, roots, true))
    }

    fun createIterators(module: Module): Collection<IndexableFilesIterator> {
      return listOf(ModuleIndexableFilesIteratorImpl(module, null, true))
    }
  }

  override fun getDebugName(): String =
    if (printRootsInDebugName) {
      val rootsDebugStr = if (roots == null) {
        "all roots"
      }
      else if (roots.isEmpty()) {
        "empty"
      }
      else {
        roots.map { it.name }.sorted().joinToString(", ", limit = 10)
      }
      "Module '" + module.name + "' ($rootsDebugStr)"
    }
    else {
      "Module '${module.name}'"
    }

  override fun getIndexingProgressText(): String =
    if (ModuleType.isInternal(module)) {
      IndexingBundle.message("indexable.files.provider.indexing.internal.module.name")
    }
    else {
      IndexingBundle.message("indexable.files.provider.indexing.module.name", module.name)
    }

  override fun getRootsScanningProgressText(): String {
    if (ModuleType.isInternal(module))
      return IndexingBundle.message("indexable.files.provider.scanning.internal.module.name")
    return IndexingBundle.message("indexable.files.provider.scanning.module.name", module.name)
  }

  override fun getOrigin(): ModuleRootOrigin = ModuleRootOriginImpl(module, roots, nonRecursiveRoots)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val index = runReadAction {
      return@runReadAction if (module.isDisposed) null else ModuleRootManager.getInstance(module).fileIndex
    }
    if (index == null) return false
    if (roots == null) {
      return index.iterateContent(fileIterator, fileFilter)
    }
    else {
      val recursiveResult = roots.all { root -> index.iterateContentUnderDirectory(root, fileIterator, fileFilter) }
      if (!recursiveResult) {
        return false
      }
      return nonRecursiveRoots?.all { root ->
        if (runReadAction { index.isInContent(root) } && fileFilter.accept(root)) fileIterator.processFile(root) else true
      } != false
    }
  }

  override fun getRootUrls(project: Project): Set<String> = module.rootManager.contentRootUrls.toSet()
}