// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.ProblemDescriptor

class IncorrectFormattingResultHandlerDefault: IncorrectFormattingResultHandler {
  override fun getResults(reportPerFile: Boolean, helper: IncorrectFormattingInspectionHelper): Array<ProblemDescriptor>? {
    return if (reportPerFile) {
      arrayOf(helper.createGlobalReport())
    }
    else {
      helper.createAllReports()
    }
  }

  override fun isCorrectHandlerForContext(globalContext: GlobalInspectionContext): Boolean = false
}