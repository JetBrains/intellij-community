// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.IntelliJThreadContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.PrefetchDataElement
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.fs.EelPrefetchApi
import com.intellij.platform.eel.fs.PrefetchedStat
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

private val LOG = logger<PrefetchCache>()

/**
 * A [PrefetchDataElement] over directory listings and stats cached on their own.
 *
 * `GrpcIjentFileSystemPosixApi` checks the cache before it makes a gRPC call, so the cache is transparent to the callers.
 *
 * Propagation layers, from the most to the least specific:
 * 1. **Coroutine context**: [ThreadContextElement] sets and restores the thread local on a coroutine dispatch.
 * 2. **IntelliJ platform executors**: [IntelliJContextElement] propagates through `ContextRunnable` and `ChildContext`,
 *    which covers DiskQueryRelay, executeOnPooledThread, invokeLater, and the others.
 */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemPosixApiPrefetchTest",
  "com.intellij.platform.ijent.impl.base.PrefetchCacheLimitTest",
)
class PrefetchCache private constructor(
  private val listings: Map<EelPath, Map<String, EelFileInfo>>,
  private val stats: Map<EelPath, PrefetchedStat>,
) : PrefetchDataElement(), IntelliJThreadContextElement<Unit> {

  // --- IntelliJContextElement ---

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override fun beforeStarted(context: CoroutineContext) {
    threadLocal.set(this)
  }

  override fun afterCompleted(context: CoroutineContext, oldState: Unit) {
    threadLocal.remove()
  }

  override fun canceled(context: CoroutineContext) {
    threadLocal.remove()
  }

  // --- Cache access ---

  override fun cachedChildren(path: EelPath): Map<String, EelFileInfo>? = listings[path]

  override fun cachedStat(path: EelPath): PrefetchedStat? = stats[path]

  override val size: Int get() = listings.size + stats.size

  /** Runs [block] with this cache in the thread context. */
  internal fun <T> runWith(block: () -> T): T = installThreadContext(currentThreadContext() + this, replace = true, block)

  companion object {
    /** A rough heap cost of one cached tree entry: the path, the name, the info and the map slots. */
    private const val BYTES_PER_ENTRY = 300

    internal fun of(stats: Map<EelPath, EelFileInfo>): PrefetchCache = PrefetchCache(emptyMap(), stats.mapValues { PrefetchedStat.of(it.value) })

    /**
     * The most tree entries one prefetch may hold. A positive [registryValue] wins.
     * Otherwise it is 10% of [maxMemory] at [BYTES_PER_ENTRY] per entry, about 358K entries at 1 GB.
     */
    fun entryLimit(registryValue: Int, maxMemory: Long): Int {
      if (registryValue > 0) return registryValue
      return (maxMemory / 10 / BYTES_PER_ENTRY).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Fills a [Builder] of at most [maxEntries] tree entries with [fill] and runs [block] with the cache.
     * When [fill] fails, or the builder has [Builder.limitExceeded] set after it, [block] runs without a cache.
     * A partial cache is never used: the server walks the roots concurrently in no order, so a truncated tree reads as deleted files.
     */
    fun <T> runWithPrefetched(maxEntries: Int, remotePathCount: Int, fill: (Builder) -> Unit, block: () -> T): T {
      val builder = Builder(maxEntries)
      val cache = try {
        fill(builder)
        if (builder.limitExceeded) {
          LOG.info("Prefetch: gave up after $maxEntries entries for $remotePathCount remote paths")
          null
        }
        else {
          builder.build()
        }
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.warn("Failed to prefetch $remotePathCount remote paths", e)
        null
      }
      if (cache == null) return block()
      LOG.info("Prefetch: ${builder.treeEntries} tree entries, ${cache.size} listings and stats cached for $remotePathCount remote paths")
      return cache.runWith(block)
    }
  }

  /**
   * Holds at most [maxEntries] tree entries over all [prefetchTrees] calls. Past it [prefetchTrees] sets [limitExceeded]
   * and stops; the caller must drop the builder, because its listings are partial.
   */
  class Builder(private val maxEntries: Int = Int.MAX_VALUE) {
    private val listings = HashMap<EelPath, HashMap<String, EelFileInfo>>()
    private val stats = HashMap<EelPath, PrefetchedStat>()
    /** The tree entries [prefetchTrees] received so far. */
    internal var treeEntries: Int = 0
      private set

    /** True once the tree entries went past `maxEntries`. Then the listings are partial and [prefetchTrees] does nothing. */
    var limitExceeded: Boolean = false
      private set

    /** The complete [children] of [directory]. A name that is not in [children] does not exist. */
    fun listing(directory: EelPath, children: Map<String, EelFileInfo>): Builder {
      listings.getOrPut(directory) { HashMap() }.putAll(children)
      return this
    }

    /** A [stat] of [path] on its own. It says nothing about a sibling. */
    fun stat(path: EelPath, stat: PrefetchedStat): Builder {
      stats[path] = stat
      return this
    }

    /**
     * Walks the trees under [roots] in one streaming RPC and caches every walked directory as a complete listing.
     * Right after the entry past `maxEntries` it sets [limitExceeded] and stops the stream, which cancels the RPC.
     * That entry is not cached. Once [limitExceeded] is set, it returns at once.
     */
    suspend fun prefetchTrees(api: EelPrefetchApi, roots: Collection<EelPath>): Builder {
      if (limitExceeded) return this
      api.prefetchDirectories(roots).takeWhile {
        limitExceeded = ++treeEntries > maxEntries
        !limitExceeded
      }.collect { (path, info) ->
        val parent = path.parent ?: return@collect
        listings.getOrPut(parent) { HashMap() }[path.fileName] = info
        when (info.type) {
          is EelFileInfo.Type.Directory -> {
            val _ = listings.getOrPut(path) { HashMap() }
          }
          is EelFileInfo.Type.Regular, is EelFileInfo.Type.Other, is EelPosixFileInfo.Type.Symlink -> Unit
        }
      }
      return this
    }

    /**
     * Stats [paths] and every ancestor of them in one RPC and caches each answer on its own.
     * The VFS stats every path component it does not hold, so the ancestors are part of the question.
     */
    suspend fun prefetchStats(api: EelPrefetchApi, paths: Collection<EelPath>): Builder {
      val withAncestors = LinkedHashSet<EelPath>()
      for (path in paths) withAncestors.addAll(generateSequence(path) { it.parent })
      for ((path, stat) in api.statMany(withAncestors)) stats[path] = stat
      return this
    }

    /** The cache, or null when nothing was cached. */
    fun build(): PrefetchCache? {
      if (listings.isEmpty() && stats.isEmpty()) return null
      return PrefetchCache(listings, stats)
    }
  }
}

/**
 * Prefetches the trees under the remote [roots] and runs [block] with the cache in the thread context.
 * A local path, a path under a direct local mount, and a file system root are skipped, because a prefetch
 * does not help them. Without a remote root [block] runs without a cache.
 */
@ApiStatus.Internal
fun <T> withPrefetchedTrees(roots: Collection<Path>, block: () -> T): T {
  return withPrefetch(roots, skipFsRoots = true, block) { api, eelPaths -> prefetchTrees(api, eelPaths) }
}

/**
 * Stats the remote [paths] with their ancestors in one RPC and runs [block] with the cache in the thread context.
 * A path that does not exist is cached as absent. A local path and a path under a direct local mount are skipped.
 */
@ApiStatus.Internal
fun <T> withPrefetchedStats(paths: Collection<Path>, block: () -> T): T {
  return withPrefetch(paths, skipFsRoots = false, block) { api, eelPaths -> prefetchStats(api, eelPaths) }
}

/**
 * Runs [block] with the [stats] the caller already holds in the thread context, for example the attributes a search
 * reports with its hits. No request is made.
 */
@ApiStatus.Internal
fun <T> withPrefetchedStats(stats: Map<EelPath, EelFileInfo>, block: () -> T): T = PrefetchCache.of(stats).runWith(block)

private fun <T> withPrefetch(
  paths: Collection<Path>,
  skipFsRoots: Boolean,
  block: () -> T,
  fill: suspend PrefetchCache.Builder.(EelPrefetchApi, List<EelPath>) -> Unit,
): T {
  if (!Registry.`is`("vfs.eel.scanning.prefetch.enabled", true)) return block()
  val remotePaths = remoteEelPaths(paths, skipFsRoots)
  if (remotePaths.isEmpty()) return block()

  // The bridge the VFS uses for its own stats: it also serves a write action, where a cancellable wait is forbidden.
  val bridge = EelBlockingBridge.getInstanceOrNull() ?: return block()
  val maxEntries = PrefetchCache.entryLimit(Registry.intValue("vfs.eel.scanning.prefetch.max.entries", 0), Runtime.getRuntime().maxMemory())
  val fillAll = { builder: PrefetchCache.Builder ->
    for ((descriptor, eelPaths) in remotePaths) {
      if (builder.limitExceeded) break
      bridge.run(descriptor) {
        val api = descriptor.toEelApi().fs as? EelPrefetchApi
        if (api != null) builder.fill(api, eelPaths)
      }
    }
  }
  return PrefetchCache.runWithPrefetched(maxEntries, remotePaths.values.sumOf { it.size }, fillAll, block)
}

private fun remoteEelPaths(paths: Collection<Path>, skipFsRoots: Boolean): Map<EelDescriptor, List<EelPath>> {
  return paths.mapNotNull { nioPath ->
    try {
      val descriptor = nioPath.getEelDescriptor()
      if (descriptor === LocalEelDescriptor) return@mapNotNull null
      val eelPath = nioPath.asEelPath()
      // a prefetch of a whole remote file system is wasteful, and a VFS refresh from a root checks only the cached children anyway
      if (skipFsRoots && eelPath.parent == null) return@mapNotNull null
      // a path under a direct local mount bypasses gRPC entirely
      if ((nioPath.fileSystem as? EelMountProvider)?.getMountRoot(eelPath) != null) return@mapNotNull null
      eelPath
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      null
    }
  }.groupBy { it.descriptor }
}
