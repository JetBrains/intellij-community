// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLambdaExpression

/**
 * @author Nikolay.Tropin
 */
class JavaSourcePositionHighlighter : SourcePositionHighlighter(), DumbAware {

  override fun getHighlightRange(sourcePosition: SourcePosition): TextRange? {
    val range = getHighlightRangeInternal(sourcePosition)
    return DebuggerUtilsEx.getHighlightingRangeInsideLine(range, sourcePosition.file, sourcePosition.line)
  }

  private fun getHighlightRangeInternal(sourcePosition: SourcePosition): TextRange? {
    // Highlight only return keyword in case of conditional return breakpoint.
    val element = sourcePosition.elementAt
    if (element != null &&
        JavaLineBreakpointType.isReturnKeyword(element) &&
        element == JavaLineBreakpointType.findSingleConditionalReturn(sourcePosition)) {
      return element.textRange
    }

    // Highlight only lambda body in case of lambda breakpoint.
    val method = DebuggerUtilsEx.getContainingMethod(sourcePosition)
    if (method is PsiLambdaExpression) {
      return (method.body ?: method).textRange
    }

    return null
  }
}
