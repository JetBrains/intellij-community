// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import java.util.function.Supplier

interface AsyncSupplier<R> : Supplier<R> {
  fun isBlocking(): Boolean =
    ApplicationManager.getApplication().isHeadlessEnvironment &&
    !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()

  /**
   * Supply a value to the consumer, when the value available
   * If [isBlocking] is true, implementation should call [consumer] before returning from the method
   */
  fun supply(consumer: (R) -> Unit, parentDisposable: Disposable)
}