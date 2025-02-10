// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.GlobalInspectionContext

class IncorrectFormattingResultHandlerProviderDefault: IncorrectFormattingResultHandlerProvider {
  override fun getApplicableResultHandler(globalContext: GlobalInspectionContext): IncorrectFormattingResultHandler? = null
}