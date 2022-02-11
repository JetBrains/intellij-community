// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable

/**
 * Describes validation requestor.
 * It can be validation requestor for any dialog data change.
 */
interface DialogValidationRequestor {

  /**
   * Subscribes on custom validation event.
   * @param parentDisposable is used to unsubscribe from validation events.
   * @param validate is callback which should be called on custom validation event.
   */
  fun subscribe(parentDisposable: Disposable? = null, validate: () -> Unit)

  companion object {
    fun create(requestor: (() -> Unit) -> Unit) =
      object : DialogValidationRequestor {
        override fun subscribe(parentDisposable: Disposable?, validate: () -> Unit) {
          requestor(validate)
        }
      }
  }
}

/**
 * Describes validation function.
 */
interface DialogValidation {

  /**
   * Validates custom dialog data.
   * @return null if custom dialog data is correct.
   */
  fun validate(): ValidationInfo?

  companion object {
    fun create(validate: () -> ValidationInfo?) =
      object : DialogValidation {
        override fun validate() = validate()
      }
  }
}
