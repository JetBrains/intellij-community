// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.refactoring.rename.impl.EmptyRenameValidator
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Checks if new name for [RenameTarget] is valid.
 *
 * @see [RenameTarget.validator]
 */
@ApiStatus.Experimental
interface RenameValidator {
  /**
   * The validate method will run on EDT on every keystroke, so it should be properly optimized.
   *
   * @return the result of validation. The refactoring will be unable to complete if the [newName] is invalid.
   * The provided message will show up as a hint to the user.
   */
  @RequiresEdt
  fun validate(newName: String): RenameValidationResult

  companion object {
    @JvmStatic
    fun empty(): RenameValidator = EmptyRenameValidator
  }

}