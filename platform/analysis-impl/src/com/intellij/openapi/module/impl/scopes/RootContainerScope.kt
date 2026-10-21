// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.RootContainer.Companion.merge
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.ActualCodeInsightContextInfo
import com.intellij.psi.search.CodeInsightContextAwareSearchScope
import com.intellij.psi.search.CodeInsightContextFileInfo
import com.intellij.psi.search.CodeInsightContextInfo
import com.intellij.psi.search.DoesNotContainFileInfo
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UnionCapableScope
import com.intellij.psi.search.createContainingContextFileInfo
import com.intellij.util.containers.SmartHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
abstract class RootContainerScope internal constructor(
  internal val rootContainer: RootContainer,
  protected val allModules: Set<Module>,
  private val containsLibraries: Boolean,
  project: Project,
) : GlobalSearchScope(project),
    CodeInsightContextAwareSearchScope,
    ActualCodeInsightContextInfo,
    UnionCapableScope {

  protected val myProjectFileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex()

  internal abstract val mainModules: Collection<Module>

  override fun contains(file: VirtualFile): Boolean {
    if (this.isSharedSourceSupportEnabled) {
      val roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)
      return roots.any { rootDescriptor -> rootContainer.findEntity(rootDescriptor) != null }
    }
    else {
      val root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file)
      return root != null && rootContainer.containsRoot(root)
    }
  }

  @ApiStatus.Experimental
  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean {
    if (!this.isSharedSourceSupportEnabled) {
      return contains(file)
    }

    // The context says which module, library or SDK the caller means; whether the file really is reached through
    // that one is for the file index to say, not for the caller - so the claimed entity is compared against the
    // entity behind each root the index reports, never used to look the scope up directly.
    val claimed = scopeEntityOf(context) ?: return false

    // One priority probe per descriptor finds the owner of each root, and only the owners get an entity probe.
    val roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)
    val result = ownersOf(roots).any { owner ->
      val entity = rootContainer.findEntity(owner)
      entity != null && isSameEntity(entity, claimed)
    }

    // don't change order of checks!
    if (!result && LOG.isDebugEnabled() && contains(file)) {
      LOG.debug("File $file is in scope, but not with $context")
    }

    return result
  }

  /**
   * [GlobalSearchScope.compare] by the rank of the root each file sits under: positive when [file1] comes first in
   * the enumeration, and a file the scope contains before one it does not. In multiverse mode a file's rank is the
   * best rank among the roots the file index reports for it.
   *
   * The multiverse rank is `(entity priority, index within the entity)`, and distinct entities of one container
   * never share a priority, so the index only decides between two files of the same entity. It is therefore only
   * computed on a priority tie: finding it is a lookup in the shared table of entity roots that a pair of files from
   * two different libraries, the usual same-FQN collision, never needs.
   *
   * @see ModuleWithDependenciesScope.compare
   */
  internal fun compareByRank(file1: VirtualFile, file2: VirtualFile): Int {
    if (!this.isSharedSourceSupportEnabled) {
      return compareRanks(classicRankOf(file1), classicRankOf(file2))
    }

    val best1 = bestRootOf(file1)
    val best2 = bestRootOf(file2)
    if (best1.priority != best2.priority || best1.priority == 0) {
      return compareRanks(best1.priority.toLong(), best2.priority.toLong())
    }
    // The same entity, and one best root each: if it is the same root, so is the position within the entity.
    if (!best1.tied && !best2.tied && best1.root == best2.root) {
      return 0
    }
    return compareRanks(best1.rank(), best2.rank())
  }

  private fun classicRankOf(file: VirtualFile): Long {
    val root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file) ?: return 0
    return rootContainer.rankOf(root)
  }

  private fun bestRootOf(file: VirtualFile): BestRoot {
    val best = BestRoot(rootContainer)
    for (rootDescriptor in myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)) {
      best.accept(rootDescriptor)
    }
    return best
  }

  private val isSharedSourceSupportEnabled: Boolean
    get() = isSharedSourceSupportEnabled(project)

  @get:ApiStatus.Experimental
  override val codeInsightContextInfo: CodeInsightContextInfo
    get() = this

  @ApiStatus.Experimental
  override fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo {
    if (!this.isSharedSourceSupportEnabled) {
      return createContainingContextFileInfo(emptyList(), contains(file))
    }

    val roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)
    if (roots.isEmpty()) {
      return DoesNotContainFileInfo()
    }

    var entityWasFoundButDidNotYieldContext = false
    val result = SmartHashSet<CodeInsightContext>()
    for (rootDescriptor in ownersOf(roots)) {
      val entity = rootContainer.findEntity(rootDescriptor)
      if (entity != null) {
        val context = findContextOf(ownerForContext(entity, rootDescriptor), project)
        if (context != null) {
          result.add(context)
        }
        else {
          // this can happen in tests when sdks are constructed on the fly and are not associated with real workspace entities
          entityWasFoundButDidNotYieldContext = true
        }
      }
    }
    return createContainingContextFileInfo(result, !result.isEmpty() || entityWasFoundButDidNotYieldContext)
  }

  /**
   * One descriptor per root among [roots]: the entity with the best priority wins, which is the first order entry
   * of the enumeration. The index reports one descriptor per entity that declares a root, while the classic
   * per-root container stored only that first owner. The context answers have to keep that shape: a second owner
   * would give the file a second context, and PSI then loads one copy of the file per context, which breaks every
   * identity comparison across the copies (IJPL-255937). IJPL-339 tracks real support for several owners.
   */
  private fun ownersOf(roots: Collection<RootDescriptor>): Collection<RootDescriptor> {
    if (roots.size <= 1) return roots

    // The descriptors of one file usually share one root: the root of a jar that two libraries declare, say.
    val root = roots.first().root
    if (roots.all { it.root == root }) {
      return listOfNotNull(firstOwnerOf(roots))
    }

    return roots.groupBy { it.root }.values.mapNotNull(::firstOwnerOf)
  }

  /** The descriptor with the best priority among [descriptors] of one root, or `null` when the scope reaches none. */
  private fun firstOwnerOf(descriptors: Collection<RootDescriptor>): RootDescriptor? {
    var best: RootDescriptor? = null
    var bestPriority = 0
    for (descriptor in descriptors) {
      val priority = rootContainer.priorityOf(descriptor)
      if (priority != 0 && (best == null || priority < bestPriority)) {
        best = descriptor
        bestPriority = priority
      }
    }
    return best
  }

  override fun isSearchInModuleContent(aModule: Module): Boolean =
    aModule in allModules

  override fun isSearchInLibraries(): Boolean =
    containsLibraries

  override fun uniteWith(scopes: Collection<GlobalSearchScope>): UnionCapableScope.UnionResult? {
    val (myBros, notMyBros) = scopes.partition { it is RootContainerScope }

    if (myBros.isEmpty()) return null

    val rootContainerScopes = (myBros + this) as List<RootContainerScope>

    val mergedContainer = rootContainerScopes.map { it.rootContainer }.merge()
    val allModules by lazy {
      rootContainerScopes.flatMap { it.allModules }.toSet()
    }

    val containsLibraries = rootContainerScopes.any { it.containsLibraries }

    val united = ModuleWithDepUnion(
      rootContainer = mergedContainer,
      mainModules = rootContainerScopes.flatMapTo(HashSet()) { it.mainModules },
      allModules = allModules,
      containsLibraries = containsLibraries,
      project = project
    )
    return UnionCapableScope.UnionResult(united, notMyBros.toSet())
  }

  override fun getProject(): Project = requireNotNull(super.getProject())

  /**
   * Structural counters of the underlying root container, for memory tests.
   *
   * @see RootContainerCounters
   */
  @TestOnly
  fun rootContainerCounters(): RootContainerCounters =
    RootContainerCounters(
      rootCount = rootContainer.size,
      entityCount = rootContainer.entityCount,
      entryValueBytes = rootContainer.entryValueBytes,
    )
}

/**
 * The structure of the root container a [RootContainerScope] keeps alive: how many roots it stands for, how many
 * entities it actually stores, and how wide the value stored next to each stored key is.
 *
 * Only this structure is exposed: the byte model that turns it into a memory estimate belongs to the test
 * that measures it, not to production code. `entityCount` is 0 for the classic container, which stores roots
 * rather than entities; for the entry-set container it is the whole of what the container costs, while
 * `rootCount` is derived from the project-wide [ScopeEntityRoots] table and costs the container nothing.
 */
@ApiStatus.Internal
class RootContainerCounters(
  @JvmField val rootCount: Int,
  @JvmField val entityCount: Int,
  /** 4: the priority and the root filter of an entity share one `int`. */
  @JvmField val entryValueBytes: Int,
)

/**
 * The root of one file that a scope ranks first, as far as entity priorities alone can tell: the full rank is only
 * computed when a second root of the same best entity turns up, or when [rank] is asked for after a tie between two
 * files.
 */
private class BestRoot(private val container: RootContainer) {
  /** The best entity priority among the file's roots, or 0 when the scope reaches none of them. */
  var priority: Int = 0
    private set

  private var descriptor: RootDescriptor? = null

  /** Whether another root of the best entity was seen, which only the index within the entity can order. */
  var tied: Boolean = false
    private set

  /** The best full rank among the tied roots, valid while [tied]. */
  private var tiedRank: Long = 0

  val root: VirtualFile? get() = descriptor?.root

  fun accept(rootDescriptor: RootDescriptor) {
    val priority = container.priorityOf(rootDescriptor)
    if (priority == 0) return
    val current = descriptor
    if (current == null || priority < this.priority) {
      this.priority = priority
      descriptor = rootDescriptor
      tied = false
    }
    else if (priority == this.priority && rootDescriptor.root != current.root) {
      if (!tied) {
        tiedRank = container.rankOf(current)
        tied = true
      }
      tiedRank = minOf(tiedRank, container.rankOf(rootDescriptor))
    }
  }

  fun rank(): Long =
    if (tied) tiedRank else descriptor?.let { container.rankOf(it) } ?: 0
}

/** Smaller ranks come first, and 0 means "not in the scope", which loses to any rank. */
private fun compareRanks(rank1: Long, rank2: Long): Int {
  if (rank1 == rank2) return 0
  if (rank1 > 0 && rank2 > 0) return rank2.compareTo(rank1)
  return if (rank1 > 0) 1 else -1
}

private val LOG = logger<RootContainerScope>()