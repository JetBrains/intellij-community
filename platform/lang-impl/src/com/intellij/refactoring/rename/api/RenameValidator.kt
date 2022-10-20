// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.model.Pointer

/**
 * Checks if new name for [RenameTarget] is valid.
 * Lifecycle: single read action.
 *
 * @see [RenameTarget]
 * @see [RenameTargetRenameValidatorFactory]
 */
interface RenameValidator {

  /**
   * @return smart pointer used to restore the [RenameValidator] instance in the subsequent read actions
   */
  fun createPointer(): Pointer<out RenameValidator>

  /**
   * @return the result of validation. The refactoring will be unable to complete if the [newName] is invalid.
   * The provided message will show up as a hint to the user.
   */
  fun validate(newName: String): RenameValidationResult

}