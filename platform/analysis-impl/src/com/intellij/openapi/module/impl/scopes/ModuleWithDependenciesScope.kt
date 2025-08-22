// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal

package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.SdkContext
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.*
import com.intellij.openapi.roots.libraries.LibraryContext
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.search.*
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.psi.search.impl.VirtualFileEnumerationAware
import com.intellij.util.ArrayUtil
import com.intellij.util.BitUtil.isSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.indexing.IndexingBundle
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*
import kotlin.concurrent.Volatile

@Suppress("EqualsOrHashCode")
class ModuleWithDependenciesScope internal constructor(
  val module: Module,
  @field:ScopeConstant
  @param:ScopeConstant
  private val myOptions: Int
) : GlobalSearchScope(module.getProject()), VirtualFileEnumerationAware, CodeInsightContextAwareSearchScope, ActualCodeInsightContextInfo {

  @Volatile
  private var myVirtualFileEnumeration: VirtualFileEnumeration? = null
  @Volatile
  private var myVFSModificationCount: Long = 0

  @Volatile
  private var myModules: Set<Module>? = null // lazy calculated, use `getModules()` instead!

  private val myProjectFileIndex: ProjectFileIndexImpl = ProjectRootManager.getInstance(module.getProject()).getFileIndex() as ProjectFileIndexImpl

  private val myRoots: RootContainer = if (this.isSharedSourceSupportEnabled) {
    MultiverseRootContainer(calcRootsMultiverse())
  }
  else {
    ClassicRootContainer(calcRoots())
  }

  private val mySingleFileSourcesTracker: SingleFileSourcesTracker = SingleFileSourcesTracker.getInstance(module.getProject())

  private fun calcRoots(): Object2IntMap<VirtualFile> {
    val en = this.orderEnumeratorForOptions.roots { entry ->
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
    val en = this.orderEnumeratorForOptions.roots { entry ->
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

  private val orderEnumeratorForOptions: OrderEnumerator
    get() {
      val en = ModuleRootManager.getInstance(this.module).orderEntries()
      en.recursively()
      if (hasOption(COMPILE_ONLY)) en.exportedOnly().compileOnly()
      if (!hasOption(LIBRARIES)) en.withoutLibraries().withoutSdk()
      if (!hasOption(MODULES)) en.withoutDepModules()
      if (!hasOption(TESTS)) en.productionOnly()
      return en
    }

  private fun calcModules(): Set<Module> {
    val modules = HashSet<Module>()
    val en = this.orderEnumeratorForOptions
    en.forEach { each ->
      if (each is ModuleOrderEntry) {
        ContainerUtil.addIfNotNull(modules, each.getModule())
      }
      else if (each is ModuleSourceOrderEntry) {
        ContainerUtil.addIfNotNull(modules, each.getOwnerModule())
      }
      true
    }
    return modules
  }

  private fun hasOption(@ScopeConstant option: Int): Boolean {
    return isSet(myOptions, option)
  }

  override fun getDisplayName(): String {
    return if (hasOption(COMPILE_ONLY))
      IndexingBundle.message("search.scope.module", module.getName())
    else
      IndexingBundle.message("search.scope.module.runtime", module.getName())
  }

  override fun isSearchInModuleContent(aModule: Module): Boolean {
    val allModules = this.modules
    return allModules.contains(aModule)
  }

  private val modules: Set<Module>
    get() {
      var allModules = myModules
      if (allModules == null) {
        allModules = HashSet(calcModules())
        myModules = allModules
      }
      return allModules
    }

  override fun isSearchInModuleContent(aModule: Module, testSources: Boolean): Boolean {
    return isSearchInModuleContent(aModule) && (hasOption(TESTS) || !testSources)
  }

  override fun isSearchInLibraries(): Boolean {
    return hasOption(LIBRARIES)
  }

  @get:ApiStatus.Experimental
  override val codeInsightContextInfo: CodeInsightContextInfo
    get() = this

  @ApiStatus.Experimental
  override fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo {
    //in case of single file source
    if (mySingleFileSourcesTracker.isSourceDirectoryInModule(file, this.module)) {
      // todo IJPL-339 support bazel in search scopes???
      return NoContextFileInfo()
    }

    val roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)
    if (roots.isEmpty()) return DoesNotContainFileInfo()

    val result = SmartHashSet<CodeInsightContext>()
    for (rootDescriptor in roots) {
      val descriptor = myRoots.getRootDescriptor(rootDescriptor)
      if (descriptor != null) {
        val context = convertToContext(descriptor)
        if (context != null) {
          result.add(context)
        }
      }
    }
    return createContainingContextFileInfo(result)
  }

  override fun contains(file: VirtualFile): Boolean {
    // in case of single file source
    if (mySingleFileSourcesTracker.isSourceDirectoryInModule(file, this.module)) return true

    if (this.isSharedSourceSupportEnabled) {
      val roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)
      return roots.any { root -> myRoots.getRootDescriptor(root) != null }
    }
    else {
      val root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file)
      return root != null && myRoots.containsRoot(root)
    }
  }

  @ApiStatus.Experimental
  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean {
    if (!this.isSharedSourceSupportEnabled) {
      return contains(file)
    }

    // in case of single file source
    if (mySingleFileSourcesTracker.isSourceDirectoryInModule(file, this.module)) {
      // todo IJPL-339 is it correct???
      if (context is ModuleContext && context.getModule() === this.module) {
        return true
      }
    }

    val rootDescriptor = convertContextToRootDescriptor(file, context) ?: return false
    val root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file) ?: return false
    val existingDescriptor = myRoots.getRootDescriptor(root) ?: return false
    val result = existingDescriptor.correspondTo(rootDescriptor)

    // don't change order of checks!
    if (!result && LOG.isDebugEnabled() && contains(file)) {
      LOG.debug("File $file is in scope, but not with $context")
    }

    return result
  }

  private fun convertToContext(descriptor: ScopeRootDescriptor): CodeInsightContext? {
    val entry = descriptor.orderEntry
    if (entry is ModuleSourceOrderEntry) {
      val module = entry.getRootModel().getModule()
      val bridge = ProjectModelContextBridge.getInstance(module.getProject())
      return bridge.getContext(module)
    }

    if (entry is LibraryOrderEntry) {
      val library = entry.getLibrary() ?: return null
      val bridge = ProjectModelContextBridge.getInstance(module.getProject())
      return bridge.getContext(library)
    }

    if (entry is JdkOrderEntry) {
      val sdk = entry.getJdk() ?: return null
      val bridge = ProjectModelContextBridge.getInstance(module.getProject())
      return bridge.getContext(sdk)
    }

    return null
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
    val modules = this.modules
    // todo might be not cheap
    if (myRoots.size > 1 && (hasOption(MODULES) && modules.size > 1 || hasOption(LIBRARIES))) {
      return VirtualFileEnumeration.EMPTY
    }

    return getFileEnumerationUnderRoots(myRoots.getRoots())
  }

  private val isSharedSourceSupportEnabled: Boolean
    get() = isSharedSourceSupportEnabled(requireNotNull(project))

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

  private fun convertContextToRootDescriptor(
    root: VirtualFile,
    context: CodeInsightContext,
  ): RootDescriptor? {
    if (context is ModuleContext) {
      val module = context.getModule()
      if (module == null) return null
      return ModuleRootDescriptor(root, module)
    }

    if (context is LibraryContext) {
      val library = context.getLibrary()
      if (library == null) return null
      return LibraryRootDescriptor(root, library)
    }

    if (context is SdkContext) {
      val sdk = context.getSdk()
      if (sdk == null) return null
      return SdkRootDescriptor(root, sdk)
    }

    return null
  }
}

private val LOG = Logger.getInstance(ModuleWithDependenciesScope::class.java)

/**
 * Compute a set of ids of all files under `roots`
 */
fun getFileEnumerationUnderRoots(roots: Collection<VirtualFile>): VirtualFileEnumeration {
  val result: IntSet = IntOpenHashSet()
  for (file in roots) {
    if (file is VirtualFileWithId) {
      val children = VirtualFileManager.getInstance().listAllChildIds(file.getId())
      result.addAll(IntList.of(*children))
    }
  }

  return MyVirtualFileEnumeration(result)
}

private class MyVirtualFileEnumeration(private val myIds: IntSet) : VirtualFileEnumeration {
  override fun contains(fileId: Int): Boolean {
    return myIds.contains(fileId)
  }

  override fun asArray(): IntArray {
    return myIds.toArray(ArrayUtil.EMPTY_INT_ARRAY)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that: MyVirtualFileEnumeration = other as MyVirtualFileEnumeration
    return myIds == that.myIds
  }

  override fun hashCode(): Int {
    return Objects.hash(myIds)
  }

  override fun toString(): String {
    return myIds.toIntArray().contentToString()
  }
}

@MagicConstant(flags = [COMPILE_ONLY.toLong(), LIBRARIES.toLong(), MODULES.toLong(), TESTS.toLong()])
private annotation class ScopeConstant

const val COMPILE_ONLY: Int = 0x01
const val LIBRARIES: Int = 0x02
const val MODULES: Int = 0x04
const val TESTS: Int = 0x08
