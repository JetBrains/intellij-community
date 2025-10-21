// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.psi.search.impl.VirtualFileEnumerationAware
import com.intellij.util.indexing.IndexingBundle
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.util.*
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

  private val myRoots: RootContainer = RootCalculator(module, myOptions).calculateRoots()

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

  override fun compare(file1: VirtualFile, file2: VirtualFile): Int {
    val r1 = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file1)
    val r2 = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file2)
    if (Comparing.equal(r1, r2)) return 0

    if (r1 == null) return -1
    if (r2 == null) return 1

    val roots = myRoots
    val i1 = roots.getPriority(r1)
    val i2 = roots.getPriority(r2)
    if (i1 == 0 && i2 == 0) return 0
    if (i1 > 0 && i2 > 0) return i2 - i1
    return if (i1 > 0) 1 else -1
  }

  @get:TestOnly
  val roots: Collection<VirtualFile>
    get() = myRoots.getSortedRoots()

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
    // todo might be not cheap
    if (myRoots.size > 1 && (hasOption(MODULES) && allModules.size > 1 || hasOption(LIBRARIES))) {
      return VirtualFileEnumeration.EMPTY
    }

    return getFileEnumerationUnderRoots(myRoots.getRoots())
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
        MultiverseRootContainer(calcRootsMultiverse())
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
      val roots = LinkedHashSet<VirtualFile>()
      Collections.addAll(roots, *en.getRoots())

      var i = 1
      val map = Object2IntOpenHashMap<VirtualFile>(roots.size)
      for (root in roots) {
        map.put(root, i++)
      }
      return map
    }

    private fun calcRootsMultiverse(): Map<VirtualFile, ScopeRootDescriptor> {
      val en = getOrderEnumeratorForOptions(module, myOptions).roots { entry ->
        if (entry is ModuleOrderEntry || entry is ModuleSourceOrderEntry) OrderRootType.SOURCES else OrderRootType.CLASSES
      }
      val entries = en.getRootEntries()

      var i = 1
      val map = HashMap<VirtualFile, ScopeRootDescriptor>(entries.size)
      for (root in entries) {
        map[root.root] = ScopeRootDescriptor(root.root, root.orderEntry, i++)
      }
      return map
    }
  }
}

private fun lazyModules(module: Module, options: Int): Set<Module> {
  val all by lazy {
    calcModules(module, options)
  }
  return all
}
