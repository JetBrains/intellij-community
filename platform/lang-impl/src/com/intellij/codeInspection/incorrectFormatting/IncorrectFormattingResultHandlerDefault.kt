// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.ProblemDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IncorrectFormattingResultHandlerDefault: IncorrectFormattingResultHandler {
  override fun getResults(reportPerFile: Boolean, helper: IncorrectFormattingInspectionHelper): Array<ProblemDescriptor>? {
    return if (reportPerFile) {
      arrayOf(helper.createGlobalReport())
    } else helper.createAllReports()
  }
}