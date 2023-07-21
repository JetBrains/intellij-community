// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.ModuleRootOriginImpl
import org.jetbrains.annotations.ApiStatus

open class ModuleIndexableFilesPolicy {
  companion object {
    fun getInstance(): ModuleIndexableFilesPolicy = service<ModuleIndexableFilesPolicy>()
  }

  open fun shouldIndexSeparateRoots(): Boolean = true
}

/**
 * @param rootHolder is null when iterator should just iterate all files in module, no explicit root list
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

    fun createIterators(module: Module, roots: IndexingRootHolder): Collection<IndexableFilesIterator> {
      // 100 is a totally magic constant here, designed to help Rider to avoid indexing all non-recursive roots with one iterator => on a
      // single thread
      if (roots.size() > 100) {
        return roots.split(100).map { rootSublists -> ModuleIndexableFilesIteratorImpl(module, rootSublists, true) }
      }
      return setOf(ModuleIndexableFilesIteratorImpl(module, roots, true))
    }

    fun createIterators(module: Module): Collection<IndexableFilesIterator> {
      return listOf(ModuleIndexableFilesIteratorImpl(module, null, true))
    }

    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Should not be used in new code; only when rolled back to old behaviour, " +
                "see DefaultProjectIndexableFilesContributor.indexProjectBasedOnIndexableEntityProviders(). " +
                "Should be removed once new code proves stable")
    fun getModuleIterators(module: Module): Collection<ModuleIndexableFilesIteratorImpl> {
      val moduleRoots = getProjectModelBasedModuleRootsToIterate(module)
      if (moduleRoots.isEmpty()) return emptyList()

      if (ModuleIndexableFilesPolicy.getInstance().shouldIndexSeparateRoots()) {
        return moduleRoots.map { ModuleIndexableFilesIteratorImpl(module, IndexingRootHolder.fromFile(it), moduleRoots.size > 1) }
      }
      return listOf(ModuleIndexableFilesIteratorImpl(module, IndexingRootHolder.fromFiles(moduleRoots.toList()), false))
    }

    private fun getProjectModelBasedModuleRootsToIterate(module: Module): Set<VirtualFile> {
      return ReadAction.compute<Set<VirtualFile>, RuntimeException> {
        if (module.isDisposed) {
          return@compute emptySet<VirtualFile>()
        }
        val result: MutableSet<VirtualFile> = LinkedHashSet()
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val projectFileIndex = ProjectFileIndex.getInstance(module.getProject())
        for (roots in listOf<Array<VirtualFile>>(
          moduleRootManager.contentRoots, moduleRootManager.sourceRoots)) {
          for (root in roots) {
            if (!projectFileIndex.isInProject(root)) continue
            val parent = root.parent
            if (parent != null) {
              val parentModule = projectFileIndex.getModuleForFile(parent, false)
              if (module == parentModule) {
                // inner content - skip it
                continue
              }
            }
            result.add(root)
          }
        }
        result
      }
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
      } ?: true
    }
  }

  override fun getRootUrls(project: Project): Set<String> = module.rootManager.contentRootUrls.toSet()
}