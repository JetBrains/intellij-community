// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.IntelliJThreadContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.PrefetchDataElement
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG = logger<PrefetchDataElementImpl>()

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
private class PrefetchDataElementImpl(private val data: Map<EelPath, Map<String, EelFileInfo>>) :
  PrefetchDataElement(),
  IntelliJThreadContextElement<Unit> {

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

  override fun getChildren(path: EelPath): Map<String, EelFileInfo>? = data[path]

  override fun lookupStat(path: EelPath): StatLookup {
    val parent = path.parent ?: return StatLookup.Miss
    val children = data[parent] ?: return StatLookup.Miss
    val info = children[path.fileName]
    return if (info != null) StatLookup.Hit(info) else StatLookup.Absent
  }

  override val size: Int get() = data.size
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
        if (info.type is EelFileInfo.Type.Directory) {
          prefetchData.getOrPut(path) { HashMap() }
        }
      }
    }

    if (prefetchData.isEmpty()) return EmptyCoroutineContext

    @Suppress("UNCHECKED_CAST")
    val element = PrefetchDataElementImpl(prefetchData as Map<EelPath, Map<String, EelFileInfo>>)
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
