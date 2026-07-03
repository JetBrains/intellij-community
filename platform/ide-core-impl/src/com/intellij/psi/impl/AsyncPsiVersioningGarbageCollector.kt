// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.impl.source.tree.mvcc.PsiVersionCleanable
import com.intellij.psi.impl.source.tree.mvcc.PsiVersioningGarbageCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
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

  /**
   * A tracker of all existing versioned references.
   *
   * We use [WeakReference] because many clients of versioned reference do not have formal lifetime guarantees and
   * they themselves rely on weak references.
   *
   * We trust that the check of [reference] happens often enough, so the weak references will be also cleaned quickly.
   */
  // todo: should we consider array-backed queue which can be cleaned atomically by a single `compareAndSet`?
  private val trackedCleanableVersions: ConcurrentLinkedQueue<WeakReference<PsiVersionCleanable>> = ConcurrentLinkedQueue()

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

  override fun registerCleanable(cleanable: PsiVersionCleanable) {
    trackedCleanableVersions.add(WeakReference(cleanable))
  }

  fun cleanupReferences(latestLiveVersions: Set<Long>) {
    val minVersion = latestLiveVersions.min() // at least one version is always alive -- the version that corresponds to read actions
    val iterator = trackedCleanableVersions.iterator()
    while (iterator.hasNext()) {
      val referent = iterator.next().get()
      if (referent == null) {
        iterator.remove()
      } else {
        referent.liveVersionChanged(minVersion, latestLiveVersions)
      }
    }
  }
}
