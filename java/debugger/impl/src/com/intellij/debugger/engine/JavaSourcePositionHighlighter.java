// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nikolay.Tropin
 */
public final class JavaSourcePositionHighlighter extends SourcePositionHighlighter implements DumbAware {
  @Override
  public TextRange getHighlightRange(SourcePosition sourcePosition) {
    TextRange range = getHighlightRangeInternal(sourcePosition);
    return DebuggerUtilsEx.getHighlightingRangeInsideLine(range, sourcePosition.getFile(), sourcePosition.getLine());
  }

  @Nullable
  private static TextRange getHighlightRangeInternal(SourcePosition sourcePosition) {
    // Highlight only return keyword in case of conditional return breakpoint.
    PsiElement element = sourcePosition.getElementAt();
    if (element != null &&
        JavaLineBreakpointType.isReturnKeyword(element) &&
        element == JavaLineBreakpointType.findSingleConditionalReturn(sourcePosition)) {
      return element.getTextRange();
    }

    // Highlight only lambda body in case of lambda breakpoint.
    PsiElement method = DebuggerUtilsEx.getContainingMethod(sourcePosition);
    if (method instanceof PsiLambdaExpression) {
      return method.getTextRange();
    }

    return null;
  }
}
