/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
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
  private final int myLambdaOrdinal;
  @Nullable
  private final SourcePosition myFirstStatementPosition;
  private final int myLastStatementLine;
  private final Range<Integer> myCallingExpressionLines;

  public LambdaMethodFilter(PsiLambdaExpression lambda, int expressionOrdinal, Range<Integer> callingExpressionLines) {
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

  @Nullable
  public SourcePosition getBreakpointPosition() {
    return myFirstStatementPosition;
  }

  public int getLastStatementLine() {
    return myLastStatementLine;
  }

  public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException {
    final VirtualMachineProxyImpl vm = process.getVirtualMachineProxy();
    final Method method = location.method();
    return DebuggerUtilsEx.isLambda(method) && (!vm.canGetSyntheticAttribute() || method.isSynthetic());
  }

  @Nullable
  @Override
  public Range<Integer> getCallingExpressionLines() {
    return myCallingExpressionLines;
  }
}
