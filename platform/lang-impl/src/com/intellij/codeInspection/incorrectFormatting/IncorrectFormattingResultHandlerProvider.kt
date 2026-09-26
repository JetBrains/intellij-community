// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface IncorrectFormattingResultHandlerProvider {
  companion object {
    val EP_NAME: ExtensionPointName<IncorrectFormattingResultHandlerProvider> = ExtensionPointName("com.intellij.resultHandlerProvider")

    fun getResultHandler(globalContext: GlobalInspectionContext?): IncorrectFormattingResultHandler {
      return when {
        globalContext == null -> IncorrectFormattingResultHandlerDefault()
        else -> EP_NAME.extensionList.map { it.getApplicableResultHandler(globalContext) }.firstOrNull()
                ?: IncorrectFormattingResultHandlerDefault()
      }
    }
  }

  fun getApplicableResultHandler(globalContext: GlobalInspectionContext): IncorrectFormattingResultHandler?
}