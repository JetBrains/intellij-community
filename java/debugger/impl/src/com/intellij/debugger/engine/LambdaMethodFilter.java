// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.util.Range;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class LambdaMethodFilter implements BreakpointStepMethodFilter {
  private final PsiLambdaExpression myLambda;
  private final int myLambdaOrdinal;
  @Nullable
  private final SourcePosition myFirstStatementPosition;
  private final int myLastStatementLine;
  private final Range<Integer> myCallingExpressionLines;

  public LambdaMethodFilter(PsiLambdaExpression lambda, int expressionOrdinal, Range<Integer> callingExpressionLines) {
    myLambda = lambda;
    myLambdaOrdinal = expressionOrdinal;
    myCallingExpressionLines = callingExpressionLines;

    SourcePosition firstStatementPosition = null;
    SourcePosition lastStatementPosition = null;
    final PsiElement body = lambda.getBody();
    if (body instanceof PsiCodeBlock) {
      final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
      if (statements.length > 0) {
        firstStatementPosition = SourcePosition.createFromElement(statements[0]);
        if (firstStatementPosition != null) {
          final PsiStatement lastStatement = statements[statements.length - 1];
          lastStatementPosition =
            SourcePosition.createFromOffset(firstStatementPosition.getFile(), lastStatement.getTextRange().getEndOffset());
        }
      }
    }
    else if (body != null) {
      firstStatementPosition = SourcePosition.createFromElement(body);
    }
    myFirstStatementPosition = firstStatementPosition;
    myLastStatementLine = lastStatementPosition != null ? lastStatementPosition.getLine() : -1;
  }

  public int getLambdaOrdinal() {
    return myLambdaOrdinal;
  }

  @Override
  @Nullable
  public SourcePosition getBreakpointPosition() {
    return myFirstStatementPosition;
  }

  @Override
  public int getLastStatementLine() {
    return myLastStatementLine;
  }

  @Override
  public boolean locationMatches(DebugProcessImpl process, Location location) {
    Method method = location.method();
    if (DebuggerUtilsEx.isLambda(method) && (!location.virtualMachine().canGetSyntheticAttribute() || method.isSynthetic())) {
      SourcePosition position = process.getPositionManager().getSourcePosition(location);
      if (position != null) {
        return ReadAction.compute(() -> DebuggerUtilsEx.inTheMethod(position, myLambda));
      }
    }
    return false;
  }

  @Nullable
  @Override
  public Range<Integer> getCallingExpressionLines() {
    return myCallingExpressionLines;
  }
}
