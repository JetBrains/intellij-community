// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.util.Range;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class AnonymousClassMethodFilter extends BasicStepMethodFilter implements BreakpointStepMethodFilter {
  private final @Nullable SourcePosition myBreakpointPosition;
  private final int myLastStatementLine;

  public AnonymousClassMethodFilter(PsiMethod psiMethod, Range<Integer> lines) {
    super(psiMethod, lines);
    SourcePosition firstStatementPosition = null;
    SourcePosition lastStatementPosition = null;
    PsiElement navigationElement = psiMethod.getNavigationElement();
    if (navigationElement instanceof PsiMethod) {
      psiMethod = (PsiMethod)navigationElement;
    }
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      final PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        firstStatementPosition = SourcePosition.createFromElement(statements[0]);
        if (firstStatementPosition != null) {
          final PsiStatement lastStatement = statements[statements.length - 1];
          lastStatementPosition = SourcePosition.createFromOffset(firstStatementPosition.getFile(), lastStatement.getTextRange().getEndOffset());
        }
      }
    }
    myBreakpointPosition = firstStatementPosition;
    myLastStatementLine = lastStatementPosition != null ? lastStatementPosition.getLine() : -1;
  }

  @Override
  public @Nullable SourcePosition getBreakpointPosition() {
    return myBreakpointPosition;
  }

  @Override
  public int getLastStatementLine() {
    return myLastStatementLine;
  }
}
