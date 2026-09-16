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
   * @param liveVersions the set of versions that can be used by at least one client
   * @param minVersion the minimum of [liveVersions]. In the majority of cases the clients want to compute the minimum of [liveVersions] for their needs.
   */
  fun liveVersionChanged(minVersion: Long, liveVersions: Set<Long>)
}