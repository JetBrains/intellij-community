// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AsyncSupplier<R> {

  /**
   * Supply a value to the consumer, when the value available
   * Note: Implementation can call [consumer] before returning from the method
   */
  fun supply(consumer: (R) -> Unit)

  companion object {

    fun <R> blocking(supplier: () -> R): AsyncSupplier<R> =
      object : AsyncSupplier<R> {
        override fun supply(consumer: (R) -> Unit) = consumer(supplier())
      }
  }
}
