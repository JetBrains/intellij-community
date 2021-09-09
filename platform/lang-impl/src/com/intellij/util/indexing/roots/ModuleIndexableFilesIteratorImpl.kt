// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ModuleFileIndexImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin
import com.intellij.util.indexing.roots.origin.ModuleRootOriginImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

open class ModuleIndexableFilesPolicy {
  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(ModuleIndexableFilesPolicy::class.java)
  }

  open fun shouldIndexSeparateRoots() = true
}

internal class ModuleIndexableFilesIteratorImpl(private val module: Module,
                                                private val roots: List<VirtualFile>,
                                                private val printRootsInDebugName: Boolean) : ModuleIndexableFilesIterator {
  companion object {

    @JvmStatic
    @ApiStatus.ScheduledForRemoval(inVersion = "22.1")
    @Deprecated("Should not be used in new code; only when rolled back to old behaviour, " +
                "see DefaultProjectIndexableFilesContributor.indexProjectBasedOnIndexableEntityProviders(). " +
                "Should be removed once new code proves stable")
    fun getModuleIterators(module: Module): Collection<ModuleIndexableFilesIteratorImpl> {
      val fileIndex = ModuleRootManager.getInstance(module).fileIndex as ModuleFileIndexImpl
      val moduleRoots = fileIndex.moduleRootsToIterate.toList()
      if (moduleRoots.isEmpty()) return emptyList()

      if (ModuleIndexableFilesPolicy.getInstance().shouldIndexSeparateRoots()) {
        return moduleRoots.map { ModuleIndexableFilesIteratorImpl(module, listOf(it), moduleRoots.size > 1) }
      }
      return listOf(ModuleIndexableFilesIteratorImpl(module, moduleRoots, false))
    }
  }

  override fun getDebugName(): String =
    if (printRootsInDebugName) {
      "Module '" + module.name + "' (" +
      if (roots.isEmpty()) "empty"
      else roots.joinToString(", ") { it.name } +
           ")"
    }
    else {
      "Module '${module.name}'"
    }

  fun getDebugDescription(): @NonNls String {
    val sb = StringBuilder("ModuleIndexableFilesIteratorImpl ")
    if (roots.isEmpty()) {
      sb.append("with no roots")
    }
    else {
      sb.append("with roots:")
      for (root in roots) {
        sb.append("\n   ").append(root)
      }
    }
    return sb.toString()
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

  override fun getOrigin(): ModuleRootOrigin = ModuleRootOriginImpl(module, roots)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    if (module.isDisposed) return false
    for (root in roots) {
      ModuleRootManager.getInstance(module).fileIndex.iterateContentUnderDirectory(root, fileIterator, fileFilter)
    }
    return true
  }

  override fun getRootUrls(): Set<String> = module.rootManager.contentRootUrls.toSet()
}