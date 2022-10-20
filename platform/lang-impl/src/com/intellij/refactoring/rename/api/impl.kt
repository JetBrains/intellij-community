// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.RefactoringBundle

internal data class RenameValidationResultData(
  private val message: @NlsContexts.DialogMessage String?,
  val level: RenameValidationResultProblemLevel,
) : RenameValidationResult {

  fun message(newName: String): @NlsContexts.DialogMessage String =
    message ?: RefactoringBundle.message("automatic.renaming.dialog.identifier.invalid.error", newName)


}

internal enum class RenameValidationResultProblemLevel {
  OK,
  WARNING,
  ERROR
}