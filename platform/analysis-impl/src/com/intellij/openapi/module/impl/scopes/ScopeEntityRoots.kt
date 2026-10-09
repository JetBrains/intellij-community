// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * The roots of every module, library and SDK the scopes have asked about, each list in the order an
 * [com.intellij.openapi.roots.OrderEnumerator] walk would produce it.
 *
 * This is the half of the old per-scope root map that was never per-scope: "which roots does this library have, and
 * in which order" is a property of the library, and the same answer was stored again in each of the ~1200 cached
 * scopes that reached it. Here it is stored once per project - about 465 000 roots in the project the investigation
 * was run on - and a scope keeps only the entities it reaches.
 *
 * The table is held strongly, not through [com.intellij.psi.util.CachedValuesManager]: its entries are what makes
 * `compare` and the test-root half of `contains` O(1), and a soft reference would hand those back to the
 * [com.intellij.openapi.roots.OrderEnumerator] walk at exactly the moment of memory pressure - the rebuild storm
 * IJPL-252901 measured. It is dropped whole on a root change instead, which is also when the scope cache is cleared.
 *
 * See `docs/IntelliJ-Platform/4_man/Lang/Shared-Sources/Module-Scope-Root-Container.md`.
 */
@Service(Level.PROJECT)
internal class ScopeEntityRoots(private val project: Project) {
  @Volatile
  private var cache: Cache = Cache(Long.MIN_VALUE, ConcurrentHashMap())

  private class Cache(@JvmField val rootsStamp: Long, @JvmField val roots: ConcurrentHashMap<ScopeEntity, EntityRootOrder>)

  // Resolved once: rootsOf runs on every multiverse compare, and a service lookup there costs more than the probe.
  private val rootManager by lazy { ProjectRootManager.getInstance(project) }

  private fun currentCache(): ConcurrentHashMap<ScopeEntity, EntityRootOrder> {
    val stamp = rootManager.modificationCount
    var cache = this.cache
    if (cache.rootsStamp != stamp) {
      cache = Cache(stamp, ConcurrentHashMap())
      this.cache = cache
    }
    return cache.roots
  }

  fun rootsOf(entity: ScopeEntity): EntityRootOrder =
    currentCache().computeIfAbsent(entity) { computeRoots(it) }

  /**
   * An id is resolved here, once per entity and root change, and not when a scope is built: a scope can outlive
   * the bridge it was built from, see [ScopeEntity]. An entity that is gone has no roots.
   */
  private fun computeRoots(entity: ScopeEntity): EntityRootOrder = when (val resolved = resolveEntity(entity, project)) {
    // The root type is not a parameter: the only scopes built out of entities take SOURCES from a module entry and
    // CLASSES from a library or an SDK one, see ModuleWithDependenciesScope.RootCalculator.
    is Module -> moduleRoots(resolved)
    is Library -> EntityRootOrder(resolved.getFiles(OrderRootType.CLASSES), null)
    is Sdk -> EntityRootOrder(resolved.rootProvider.getFiles(OrderRootType.CLASSES), null)
    else -> EntityRootOrder(VirtualFile.EMPTY_ARRAY, null)
  }

  /**
   * Content entries in order, source folders in order, nulls skipped - exactly what
   * [com.intellij.openapi.roots.ModuleRootModel.getSourceRoots] does, which is where the enumerator takes them
   * from. Test-ness is recorded alongside instead of filtering, because the `TESTS` option differs per scope while
   * this list is shared by all of them.
   */
  private fun moduleRoots(module: Module): EntityRootOrder {
    val roots = ArrayList<VirtualFile>()
    val testRoots = ArrayList<Boolean>()
    var anyTestRoot = false
    for (contentEntry in ModuleRootManager.getInstance(module).contentEntries) {
      for (sourceFolder in contentEntry.sourceFolders) {
        val file = sourceFolder.file ?: continue
        roots.add(file)
        val isTest = sourceFolder.isTestSource
        anyTestRoot = anyTestRoot || isTest
        testRoots.add(isTest)
      }
    }
    return EntityRootOrder(roots.toTypedArray(),
                           if (anyTestRoot) BooleanArray(testRoots.size) { testRoots[it] } else null)
  }

  /** Roots per entity currently in the shared table, for the memory test's byte model. */
  @TestOnly
  fun cachedRootCounts(): IntArray = currentCache().values.map { it.size }.toIntArray()

  companion object {
    fun getInstance(project: Project): ScopeEntityRoots = project.service()
  }
}

/**
 * One entity's roots in enumeration order, plus which of them are test roots.
 *
 * The index of a root in this list is the second half of a scope's ordering key: a scope ranks entities, and within
 * one entity the roots keep the order the entity declares them in. That is what preserves the jar order inside a
 * library, which `compare` uses to pick between two classes with the same FQN.
 */
internal class EntityRootOrder(
  @JvmField val roots: Array<VirtualFile>,
  /** `null` when no root here is a test root, which is every library and SDK and most modules. */
  private val testRoots: BooleanArray?,
) {
  /** root -> index + 1, so that the fastutil default of 0 means "not a root of this entity". */
  private val indexByRoot: Object2IntMap<VirtualFile> = Object2IntOpenHashMap<VirtualFile>(roots.size).also { map ->
    for (index in roots.indices) {
      map.putIfAbsent(roots[index], index + 1)
    }
    map.trim()
  }

  val size: Int get() = roots.size

  /** Index of [root] among this entity's roots, or -1. */
  fun indexOf(root: VirtualFile): Int = indexByRoot.getInt(root) - 1

  fun isTestRoot(index: Int): Boolean = testRoots != null && testRoots[index]

  val hasTestRoots: Boolean get() = testRoots != null
}
