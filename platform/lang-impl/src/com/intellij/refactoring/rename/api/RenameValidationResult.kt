// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.RefactoringBundle

/**
 * The result of name validation during rename refactoring.
 *
 * @see [RenameValidator]
 */
sealed interface RenameValidationResult {

  companion object {
    /**
     * The result when everything is OK with the name and refactoring is safe to proceed.
     */
    @JvmStatic
    fun ok(): RenameValidationResult = OK

    /**
     * The result when user is advised to not use a particular name (e.g. due to naming conventions),
     * but the refactoring is safe to proceed and will not result in a broken code.
     *
     * The provided [message] will be shown to the user.
     */
    @JvmStatic
    fun warn(message: @NlsContexts.DialogMessage String): RenameValidationResult {
      return RenameValidationResultData(message, level = RenameValidationResultProblemLevel.WARNING)
    }

    /**
     * The result when provided new name is invalid and refactoring could result in a broken code.
     * This result will prevent refactoring from finish.
     *
     * A standard message that the identifier is invalid will be shown to the user.
     */
    @JvmStatic
    fun invalid(): RenameValidationResult {
      return RenameValidationResultData(null, level = RenameValidationResultProblemLevel.ERROR)
    }

    /**
     * The result when provided new name is invalid and refactoring could result in a broken code.
     * This result will prevent refactoring from finish.
     *
     * The provided [message] will be shown to the user.
     */
    @JvmStatic
    fun invalid(message: @NlsContexts.DialogMessage String): RenameValidationResult {
      return RenameValidationResultData(message, level = RenameValidationResultProblemLevel.ERROR)
    }

    internal object OK: RenameValidationResult

    internal data class RenameValidationResultData(
      private val message: @NlsContexts.DialogMessage String?,
      val level: RenameValidationResultProblemLevel,
    ) : RenameValidationResult {

      fun message(newName: String): @NlsContexts.DialogMessage String =
        message ?: RefactoringBundle.message("automatic.renaming.dialog.identifier.invalid.error", newName)

    }

    internal enum class RenameValidationResultProblemLevel {
      WARNING,
      ERROR
    }
  }
}