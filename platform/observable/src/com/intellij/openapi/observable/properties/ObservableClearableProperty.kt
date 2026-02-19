// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

/**
 * Deprecated. Please don't use this interface directly.
 * Clearable features of this interface aren't used.
 * Callback [afterChange] cannot be called when lazy property is [reset].
 * @see AtomicLazyProperty
 */
@Deprecated("Use instead ObservableMutableProperty")
@ApiStatus.ScheduledForRemoval
interface ObservableClearableProperty<T> : ObservableMutableProperty<T> {

  @Deprecated("Use set instead")
  @ApiStatus.ScheduledForRemoval
  fun reset() {}

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  fun afterReset(listener: () -> Unit) {}

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {}
}