// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext

@Internal
interface CoroutineSupport {

  /**
   * Defines the behavior of a dispatcher that manages Event Dispatch Thread.
   * The behavior of the dispatchers differs in their treatment of the Read-Write lock
   * and possibility of interaction with the IntelliJ Platform model (PSI, VFS, etc.)
   * By default, consider using [RELAX].
   */
  enum class UiDispatcherKind {
    /**
     * This UI dispatcher **forbids** any attempt to access the RW lock.
     * Use it if you are performing strictly UI-related computations.
     */
    STRICT,

    /**
     * This UI dispatcher **allows** taking the RW lock, but **does not** acquire it by default.
     * Use it for incremental migration from [LEGACY].
     */
    RELAX,

    /**
     * This UI dispatcher **acquires** the Write-Intent lock for all computations by default.
     * We would like to move away from unconditional acquisition of the Read-Write lock, so please use [RELAX] for replacement.
     */
    @ApiStatus.Obsolete
    LEGACY;
  }

  fun uiDispatcher(kind: UiDispatcherKind, immediate: Boolean): CoroutineContext
}
