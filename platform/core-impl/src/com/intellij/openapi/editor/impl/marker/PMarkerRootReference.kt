// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

/**
 * Holds one root of a marker store.
 * Each successful write calls [MarkerRootUpdater.onRootPublished] of the owner before it returns.
 * So no write can skip the update of [SnapshotMarkerRootStore.CurrentRootCache.rootIdentity].
 * The reference does not retain the owner.
 *
 * @property updater the owner that receives the publications, or null when no owner caches the root.
 */
class PMarkerRootReference internal constructor(
  @Volatile private var root: PMarkerRoot,
  private val updater: WeakReference<MarkerRootUpdater>?,
) {
  fun get(): PMarkerRoot = root

  fun set(newRoot: PMarkerRoot) {
    root = newRoot
    updater?.get()?.onRootPublished(this)
  }

  fun compareAndSet(expectedRoot: PMarkerRoot, newRoot: PMarkerRoot): Boolean {
    if (!ROOT_UPDATER.compareAndSet(this, expectedRoot, newRoot)) return false
    updater?.get()?.onRootPublished(this)
    return true
  }

  override fun toString(): String = "PMarkerRootReference($root)"

  private companion object {
    private val ROOT_UPDATER: AtomicReferenceFieldUpdater<PMarkerRootReference, PMarkerRoot> =
      AtomicReferenceFieldUpdater.newUpdater(PMarkerRootReference::class.java, PMarkerRoot::class.java, "root")
  }
}
