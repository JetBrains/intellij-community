// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.debug

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Identity-keyed, weakly-referencing map from objects to counters. Keys are compared by `===` and hashed with
 * [System.identityHashCode], so a key overriding `equals`/`hashCode` can neither collide with another instance nor be
 * kept alive. Entries of collected keys are dropped on the next access. Not thread-safe; callers synchronize.
 */
internal class WeakIdentityCounters {
  private val queue = ReferenceQueue<Any>()
  private val map = HashMap<IdentityRef, AtomicInteger>()

  fun add(key: Any) {
    expunge()
    map.putIfAbsent(IdentityRef(key, queue), AtomicInteger())
  }

  fun remove(key: Any) {
    expunge()
    map.remove(IdentityRef(key, null))
  }

  fun get(key: Any): AtomicInteger? {
    expunge()
    return map[IdentityRef(key, null)]
  }

  fun keys(): List<Any> {
    expunge()
    return map.keys.mapNotNull { it.get() }
  }

  fun isEmpty(): Boolean {
    expunge()
    return map.isEmpty()
  }

  fun clear() {
    map.clear()
    expunge()
  }

  private fun expunge() {
    while (true) {
      val ref = queue.poll() ?: return
      map.remove(ref)
    }
  }

  private class IdentityRef(referent: Any, queue: ReferenceQueue<Any>?) : WeakReference<Any>(referent, queue) {
    private val hash = System.identityHashCode(referent)

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is IdentityRef) return false
      val referent = get()
      return referent != null && referent === other.get()
    }
  }
}
