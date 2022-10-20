// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.annotations.ApiStatus

/**
 * The result of name validation during rename refactoring.
 *
 * @see [RenameValidator]
 */
@ApiStatus.NonExtendable
sealed interface RenameValidationResult {

  companion object {
    /**
     * The result when everything is OK with the name and refactoring is safe to proceed.
     */
    @JvmStatic
    fun ok(): RenameValidationResult =
      RenameValidationResultData(null, level = RenameValidationResultProblemLevel.OK)

    /**
     * The result when user is advised to not use a particular name (e.g. due to naming conventions),
     * but the refactoring is safe to proceed and will not result in a broken code.
     *
     * The provided [message] will be shown to the user.
     */
    @JvmStatic
    fun warn(message: @NlsContexts.DialogMessage String): RenameValidationResult =
      RenameValidationResultData(message, level = RenameValidationResultProblemLevel.WARNING)

    /**
     * The result when provided new name is invalid and refactoring could result in a broken code.
     * This result will prevent refactoring from finish.
     *
     * A standard message that the identifier is invalid will be shown to the user.
     */
    fun invalid(): RenameValidationResult =
      RenameValidationResultData(null, level = RenameValidationResultProblemLevel.ERROR)

    /**
     * The result when provided new name is invalid and refactoring could result in a broken code.
     * This result will prevent refactoring from finish.
     *
     * The provided [message] will be shown to the user.
     */
    @JvmStatic
    fun invalid(message: @NlsContexts.DialogMessage String): RenameValidationResult =
      RenameValidationResultData(message, level = RenameValidationResultProblemLevel.ERROR)

  }
}