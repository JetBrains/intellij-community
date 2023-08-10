// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation

import com.intellij.openapi.ui.ValidationInfo

/**
 * Describes validation function.
 */
fun interface DialogValidation {

  /**
   * Validates custom dialog data.
   * @return null if custom dialog data is correct.
   */
  fun validate(): ValidationInfo?

  /**
   * Defines validation with parameter.
   */
  fun interface WithParameter<in T> {
    fun curry(parameter: T): DialogValidation
  }

  /**
   * Defines validation with two parameters.
   */
  fun interface WithTwoParameters<in T1, in T2> {
    fun curry(parameter: T1): WithParameter<T2>
  }
}