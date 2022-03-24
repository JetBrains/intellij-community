// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation

import com.intellij.openapi.Disposable

/**
 * Describes validation requestor.
 * It can be validation requestor for any dialog data change.
 */
fun interface DialogValidationRequestor {

  /**
   * Subscribes on custom validation event.
   * @param parentDisposable is used to unsubscribe from validation events.
   * @param validate is callback which should be called on custom validation event.
   */
  fun subscribe(parentDisposable: Disposable?, validate: () -> Unit)

  /**
   * Defines validation requestor with parameter.
   */
  fun interface WithParameter<in T> {
    operator fun invoke(parameter: T): DialogValidationRequestor
  }
}