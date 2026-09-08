// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.PsiVersionCleanable
import com.intellij.psi.impl.source.tree.mvcc.PsiVersioningGarbageCollector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * This class performs asynchronous periodic cleanup of unused versions.
 *
 * We could have potentially many versioned objects, so we need to perform debounce of the incoming requests for garbage collection.
 * This concerns both changes in live versions -- as they can change on every write action -- and changes in the set of strongly reachable cleanable objects.
 * Temporary versioned objects should not contribute to the overhead of garbage collection.
 */
internal class AsyncPsiVersioningGarbageCollector(val scope: CoroutineScope) : PsiVersioningGarbageCollector {

  private val versionCleanables = ConcurrentHashMap<Long, ConcurrentLinkedQueue<WeakReference<PsiVersionCleanable>>>()

  private val liveVersions: AtomicReference<Set<Long>> = AtomicReference()
  private val timeoutQueue: Channel<Unit> = Channel()

  private val actualCleanupScope = scope.childScope("Actual stale version cleaner")

  init {
    scope.launch {
      while (true) {
        delay(1.seconds)
        timeoutQueue.send(Unit)
      }
    }

    scope.launch {
      while (true) {
        timeoutQueue.receive()
        val currentLiveVersions = liveVersions.getAndSet(null)
        if (currentLiveVersions != null) {
          actualCleanupScope.launch {
            cleanupReferences(currentLiveVersions)
          }
        }
      }
    }
  }

  override fun liveVersionsChanged(latestLiveVersions: Set<Long>) {
    liveVersions.set(latestLiveVersions)
  }

  override suspend fun awaitCleanup() {
    timeoutQueue.send(Unit)
    for (job in actualCleanupScope.coroutineContext.job.children.toList()) {
      job.join()
    }
  }

  override fun registerCleanablesForVersion(version: Long, cleanables: Collection<PsiVersionCleanable>) {
    if (cleanables.isEmpty()) return
    val references = cleanables.map(::WeakReference)
    addReferences(version, references)
    liveVersionsChanged(InternalPsiVersioning.PsiVersionRegistry.instance.getFrozenKeys())
  }

  private fun addReferences(version: Long, references: Collection<WeakReference<PsiVersionCleanable>>) {
    versionCleanables.compute(version) { _, bucket ->
      (bucket ?: ConcurrentLinkedQueue()).apply { addAll(references) }
    }
  }

  fun cleanupReferences(latestLiveVersions: Set<Long>) {
    val minVersion = latestLiveVersions.min() // at least one version is always alive -- the version that corresponds to read actions
    for (version in versionCleanables.keys) {
      if (version > minVersion) continue
      val bucket = versionCleanables.remove(version) ?: continue
      for (reference in bucket) {
        val cleanable = reference.get() ?: continue
        cleanable.liveVersionChanged(minVersion, latestLiveVersions)
      }
    }
  }

  override fun cleanupNow() {
    cleanupReferences(InternalPsiVersioning.PsiVersionRegistry.instance.getFrozenKeys())
  }

  override fun pendingVersionCleanableCount(): Int = versionCleanables.values.sumOf { it.size }
}
