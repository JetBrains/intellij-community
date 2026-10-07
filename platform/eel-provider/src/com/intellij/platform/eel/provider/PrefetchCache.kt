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
import kotlinx.coroutines.ThreadContextElement
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
    internal fun of(stats: Map<EelPath, EelFileInfo>): PrefetchCache = PrefetchCache(emptyMap(), stats.mapValues { PrefetchedStat.of(it.value) })
  }

  class Builder {
    private val listings = HashMap<EelPath, HashMap<String, EelFileInfo>>()
    private val stats = HashMap<EelPath, PrefetchedStat>()

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

    /** Walks the trees under [roots] in one streaming RPC and caches every walked directory as a complete listing. */
    suspend fun prefetchTrees(api: EelPrefetchApi, roots: Collection<EelPath>): Builder {
      api.prefetchDirectories(roots).collect { (path, info) ->
        val parent = path.parent ?: return@collect
        listings.getOrPut(parent) { HashMap() }[path.fileName] = info
        when (info.type) {
          is EelFileInfo.Type.Directory -> listings.getOrPut(path) { HashMap() }
          is EelFileInfo.Type.Regular, is EelFileInfo.Type.Other, is EelPosixFileInfo.Type.Symlink -> Unit
        }
      }
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
  val cache = try {
    val builder = PrefetchCache.Builder()
    for ((descriptor, eelPaths) in remotePaths) {
      bridge.run(descriptor) {
        val api = descriptor.toEelApi().fs as? EelPrefetchApi
        if (api != null) builder.fill(api, eelPaths)
      }
    }
    builder.build()
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn("Failed to prefetch ${paths.size} remote paths", e)
    null
  }
  if (cache == null) return block()
  LOG.info("Prefetch: ${cache.size} entries cached for ${remotePaths.values.sumOf { it.size }} remote paths")
  return cache.runWith(block)
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
