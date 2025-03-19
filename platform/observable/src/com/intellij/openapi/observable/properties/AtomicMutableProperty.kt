// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import org.jetbrains.annotations.ApiStatus

/**
 * An observable property that may be updated atomically.
 */
@ApiStatus.Internal
interface AtomicMutableProperty<T> : ObservableMutableProperty<T> {

  /**
   * Atomically updates property value.
   * @param update is value transformation function.
   * @return new value of property
   */
  fun updateAndGet(update: (T) -> T): T
}