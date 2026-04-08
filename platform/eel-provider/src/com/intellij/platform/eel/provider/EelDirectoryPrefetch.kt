// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG = logger<PrefetchDataElement>()

/**
 * Coroutine context element carrying prefetched directory data.
 *
 * Checked by `GrpcIjentFileSystemPosixApi` before making gRPC calls,
 * making the cache fully transparent to all callers.
 *
 * Propagation layers (from most to least specific):
 * 1. **Coroutine context** — [ThreadContextElement] sets/restores the ThreadLocal on coroutine dispatch.
 * 2. **IntelliJ platform executors** — [IntelliJContextElement] propagates through `ContextRunnable`/`ChildContext`,
 *    covering DiskQueryRelay, executeOnPooledThread, invokeLater, etc.
 *
 * @param data directory path → (childName → fileInfo), immutable after construction
 */
@ApiStatus.Internal
class PrefetchDataElement(private val data: Map<EelPath, Map<String, EelFileInfo>>) :
  AbstractCoroutineContextElement(Key),
  ThreadContextElement<PrefetchDataElement?>,
  IntelliJContextElement {

  // --- ThreadContextElement ---

  override fun updateThreadContext(context: CoroutineContext): PrefetchDataElement? {
    val prev = threadLocal.get()
    threadLocal.set(this)
    return prev
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: PrefetchDataElement?) {
    threadLocal.set(oldState)
  }

  // --- IntelliJContextElement ---

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
  override fun beforeChildStarted(context: CoroutineContext) { threadLocal.set(this) }
  override fun afterChildCompleted(context: CoroutineContext) { threadLocal.remove() }
  override fun childCanceled(context: CoroutineContext) { threadLocal.remove() }

  // --- Cache access ---

  fun getChildren(path: EelPath): Map<String, EelFileInfo>? = data[path]

  sealed class StatLookup {
    class Hit(val info: EelFileInfo) : StatLookup()
    object Absent : StatLookup()  // parent cached, child missing = known DoesNotExist
    object Miss : StatLookup()    // parent not cached = need gRPC
  }

  fun lookupStat(path: EelPath): StatLookup {
    val parent = path.parent ?: return StatLookup.Miss
    val children = data[parent] ?: return StatLookup.Miss
    val info = children[path.fileName]
    return if (info != null) StatLookup.Hit(info) else StatLookup.Absent
  }

  val size: Int get() = data.size

  companion object Key : CoroutineContext.Key<PrefetchDataElement> {
    private val threadLocal = ThreadLocal<PrefetchDataElement?>()

    @ApiStatus.Internal
    suspend fun current(): PrefetchDataElement? {
      return currentCoroutineContext()[PrefetchDataElement] ?: threadLocal.get()
    }
  }
}

/**
 * Prefetches remote directory trees via a single streaming RPC per descriptor.
 * Returns a [CoroutineContext] with [PrefetchDataElement] carrying the results.
 */
@ApiStatus.Internal
suspend fun buildPrefetchContext(remoteRoots: List<Pair<EelDescriptor, EelPath>>): CoroutineContext {
  if (remoteRoots.isEmpty()) return EmptyCoroutineContext

  try {
    val prefetchData = HashMap<EelPath, HashMap<String, EelFileInfo>>(maxOf(remoteRoots.size * 20, 256))
    val rootsByDescriptor = remoteRoots.groupBy(keySelector = { it.first }, valueTransform = { it.second })

    for ((descriptor, roots) in rootsByDescriptor) {
      val prefetcher = descriptor.toEelApi().fs as? com.intellij.platform.eel.fs.DirectoryPrefetcher ?: continue
      prefetcher.prefetchDirectories(roots).collect { (path, info) ->
        val parent = path.parent ?: return@collect
        prefetchData.getOrPut(parent) { HashMap() }[path.fileName] = info
      }
    }

    if (prefetchData.isEmpty()) return EmptyCoroutineContext

    @Suppress("UNCHECKED_CAST")
    val element = PrefetchDataElement(prefetchData as Map<EelPath, Map<String, EelFileInfo>>)
    LOG.info("Prefetched ${element.size} directories for scanning")
    return element
  }
  catch (e: java.util.concurrent.CancellationException) {
    throw e
  }
  catch (e: Exception) {
    LOG.warn("Failed to prefetch remote directory trees", e)
    return EmptyCoroutineContext
  }
}
