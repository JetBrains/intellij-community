// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import org.jetbrains.annotations.ApiStatus

/**
 * An interface for objects that need to react to changes in the set of live PSI versions.
 *
 * An example is an object that keeps track of several references (e.g. [VersionedPsiReference] or [ConcurrentWeakVersionedValueHashMap]).
 * Such an object likely needs to remove obsolete versions to improve memory usage.
 *
 * An implementation registers a successful change through [InternalPsiVersioning.recordVersionedChange] when this object is modified.
 * The collector keeps a weak reference until it processes that change.
 */
@ApiStatus.Internal
interface PsiVersionCleanable {

  /**
   * Called when the set of live versions is changed.
   *
   * This function is expected to be fast and context-independent, as it can be called in any environment.
   *
   * @param minVersion the threshold after which reachable versions should remain. Always belongs to the main timeline.
   */
  fun liveVersionChanged(minVersion: Long)
}