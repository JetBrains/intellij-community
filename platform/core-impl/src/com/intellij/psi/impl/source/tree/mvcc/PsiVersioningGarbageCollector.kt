// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly


/**
 * Garbage collector for versioned objects.
 *
 * A set of versions is constantly changing,
 * and it is important to periodically clean up the objects referenced alongside unused versions
 * to make obsolete objects eligible for JVM garbage collections.
 */
@ApiStatus.Internal
interface PsiVersioningGarbageCollector {

  /**
   * Registers [cleanable] as eligible for garbage collection.
   */
  fun registerCleanable(cleanable: PsiVersionCleanable)

  /**
   * This method needs to be called by the Platform when it detects that the set of live versions is now different.
   */
  fun liveVersionsChanged(latestLiveVersions: Set<Long>)

  /**
   * Used by test code to await garbage collection if it is asynchronous.
   */
  @TestOnly
  suspend fun awaitCleanup()
}