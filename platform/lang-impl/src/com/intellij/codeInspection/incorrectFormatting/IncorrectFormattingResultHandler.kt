// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.extensions.ExtensionPointName

interface IncorrectFormattingResultHandler {
  companion object {
    val EP_NAME: ExtensionPointName<IncorrectFormattingResultHandler> = ExtensionPointName("com.intellij.resultHandler")

    fun getResultHandler(globalContext: GlobalInspectionContext?): IncorrectFormattingResultHandler {
      return when {
        globalContext == null -> IncorrectFormattingResultHandlerDefault()
        else -> EP_NAME.extensionList.firstOrNull { it.isCorrectHandlerForContext(globalContext) }
                ?: IncorrectFormattingResultHandlerDefault()
      }
    }
  }

  fun getResults(reportPerFile: Boolean, helper: IncorrectFormattingInspectionHelper): Array<ProblemDescriptor>?

  fun isCorrectHandlerForContext(globalContext: GlobalInspectionContext): Boolean
}