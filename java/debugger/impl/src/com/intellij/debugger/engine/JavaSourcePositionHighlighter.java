// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.util.DocumentUtil;

/**
 * @author Nikolay.Tropin
 */
public class JavaSourcePositionHighlighter extends SourcePositionHighlighter implements DumbAware {
  @Override
  public TextRange getHighlightRange(SourcePosition sourcePosition) {
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
      TextRange range = method.getTextRange();
      PsiFile file = sourcePosition.getFile();
      if (range != null) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document != null) {
          TextRange lineRange = DocumentUtil.getLineTextRange(document, sourcePosition.getLine());
          TextRange res = range.intersection(lineRange);
          return lineRange.equals(res) ? null : res; // highlight the whole line for multiline lambdas
        }
      }
      return range;
    }

    return null;
  }
}
