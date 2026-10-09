// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.psi.search.impl.VirtualFileEnumerationAware
import com.intellij.util.indexing.IndexingBundle
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import kotlin.concurrent.Volatile

@Suppress("EqualsOrHashCode")
class ModuleWithDependenciesScope internal constructor(
  val module: Module,
  @field:ScopeConstant
  @param:ScopeConstant
  private val myOptions: Int,
) : RootContainerScope(
  rootContainer = RootCalculator(module, myOptions).calculateRoots(),
  allModules = lazyModules(module, myOptions),
  containsLibraries = myOptions.hasOption(LIBRARIES),
  project = module.getProject()
), VirtualFileEnumerationAware {

  @Volatile
  private var myVirtualFileEnumeration: VirtualFileEnumeration? = null

  @Volatile
  private var myVFSModificationCount: Long = 0

  override val mainModules: List<Module>
    get() = listOf(module)

  private fun hasOption(@ScopeConstant option: Int): Boolean = myOptions.hasOption(option)

  override fun getDisplayName(): String {
    return if (hasOption(COMPILE_ONLY))
      IndexingBundle.message("search.scope.module", module.getName())
    else
      IndexingBundle.message("search.scope.module.runtime", module.getName())
  }

  override fun isSearchInModuleContent(aModule: Module, testSources: Boolean): Boolean {
    return isSearchInModuleContent(aModule) && (hasOption(TESTS) || !testSources)
  }

  override fun compare(file1: VirtualFile, file2: VirtualFile): Int =
    compareByRank(file1, file2)

  @get:TestOnly
  val roots: Collection<VirtualFile>
    get() = rootContainer.getSortedRoots()

  override fun extractFileEnumeration(): VirtualFileEnumeration? {
    var enumeration = myVirtualFileEnumeration
    val currentVFSStamp = VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.getModificationCount()
    if (currentVFSStamp != myVFSModificationCount) {
      enumeration = doExtractFileEnumeration()
      myVirtualFileEnumeration = enumeration
      myVFSModificationCount = currentVFSStamp
    }
    return if (enumeration === VirtualFileEnumeration.EMPTY) null else enumeration
  }

  private fun doExtractFileEnumeration(): VirtualFileEnumeration {
    // approximateSize, not size: this runs on every VFS structure change, and the exact root count of the entry-set
    // container means materialising its roots. The bound only ever overestimates, so a scope it rejects here is one
    // that would have been rejected anyway or a single-root scope that loses an optimisation.
    if (rootContainer.approximateSize > 1 && (hasOption(MODULES) && allModules.size > 1 || hasOption(LIBRARIES))) {
      return VirtualFileEnumeration.EMPTY
    }

    return getFileEnumerationUnderRoots(rootContainer.getRoots())
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val that = other as ModuleWithDependenciesScope
    return myOptions == that.myOptions && this.module == that.module
  }

  override fun calcHashCode(): Int {
    return 31 * module.hashCode() + myOptions
  }

  override fun toString(): String {
    return "Module-with-dependencies:" + module.getName() +
           " compile-only:" + hasOption(COMPILE_ONLY) +
           " include-libraries:" + hasOption(LIBRARIES) +
           " include-other-modules:" + hasOption(MODULES) +
           " include-tests:" + hasOption(TESTS)
  }

  private class RootCalculator(
    val module: Module,
    val myOptions: Int,
  ) {
    fun calculateRoots(): RootContainer {
      return if (isSharedSourceSupportEnabled(module.project)) {
        calcRootsMultiverse()
      }
      else {
        ClassicRootContainer(calcRoots())
      }
    }

    private fun calcRoots(): Object2IntMap<VirtualFile> {
      val en = getOrderEnumeratorForOptions(module, myOptions).roots { entry ->
        if (entry is ModuleOrderEntry || entry is ModuleSourceOrderEntry) return@roots OrderRootType.SOURCES
        OrderRootType.CLASSES
      }
      val roots = en.getRoots()
      // Priorities are a dense 1-based index over the distinct roots: only their order matters, and 0 has to keep
      // meaning "absent". The map does the deduplication itself, so no intermediate set is built for what can be
      // tens of thousands of roots.
      val map = Object2IntOpenHashMap<VirtualFile>(roots.size)
      var i = 1
      for (root in roots) {
        if (!map.containsKey(root)) {
          map.put(root, i++)
        }
      }
      map.trim()
      return map
    }

    private fun calcRootsMultiverse(): EntrySetRootContainer {
      val en = getOrderEnumeratorForOptions(module, myOptions).roots { entry ->
        if (entry is ModuleOrderEntry || entry is ModuleSourceOrderEntry) OrderRootType.SOURCES else OrderRootType.CLASSES
      }
      // Deliberately getEnumeratedEntries() and not getRootEntries(): the roots behind an entry are a property of
      // the entry's owner and are kept once per project, so building a scope never resolves one.
      return EntrySetRootContainer.build(module.project, en.getEnumeratedEntries())
    }
  }
}

private fun lazyModules(module: Module, options: Int): Set<Module> {
  val all by lazy {
    calcModules(module, options)
  }
  return all
}
