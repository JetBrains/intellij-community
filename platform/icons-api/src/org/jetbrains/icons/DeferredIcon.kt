// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface DeferredIcon: Icon {
  val isDone: Boolean
  val placeholder: Icon?
}

@ApiStatus.Experimental
interface AsyncDeferredIcon: DeferredIcon {
  suspend fun resolveInPlaceAsync(): Icon
}

@ApiStatus.Experimental
interface SyncDeferredIcon: DeferredIcon {
  fun resolveInPlace(): Icon
}
