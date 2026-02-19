// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Range;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.Location;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class StepOutOfBlockActionUtils {

  private static void doStepOutOfBlock(@NotNull XDebugSession xSession) {
    XDebugProcess process = xSession.getDebugProcess();
    if (process instanceof JavaDebugProcess) {
      DebuggerContextImpl debuggerContext = ((JavaDebugProcess)process).getDebuggerSession().getContextManager().getContext();
      DebuggerSession session = debuggerContext.getDebuggerSession();
      SourcePosition position = debuggerContext.getSourcePosition();
      if (position != null && session != null) {
        PsiElement element = position.getElementAt();
        PsiElement block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class, PsiLambdaExpression.class);
        if (block instanceof PsiCodeBlock) {
          PsiElement parent = block.getParent();
          if (!(parent instanceof PsiMethod) && !(parent instanceof PsiLambdaExpression)) {
            TextRange textRange = block.getTextRange();
            Document document = FileDocumentManager.getInstance().getDocument(position.getFile().getVirtualFile());
            if (document != null) {
              int startLine = document.getLineNumber(textRange.getStartOffset());
              int endLine = document.getLineNumber(textRange.getEndOffset());
              session.sessionResumed();
              session.stepOver(false, new BlockFilter(startLine, endLine), StepRequest.STEP_LINE);
              return;
            }
          }
        }
      }
    }
    xSession.stepOut();
  }

  @ApiStatus.Internal
  public static void stepOutOfBlock(@NotNull XDebugSession xSession) {
    doStepOutOfBlock(xSession);
  }

  private static final class BlockFilter implements MethodFilter {
    private final Range<Integer> myLines;

    private BlockFilter(int startLine, int endLine) {
      myLines = new Range<>(startLine, endLine);
    }

    @Override
    public boolean locationMatches(DebugProcessImpl process, Location location) {
      return false;
    }

    @Override
    public @NotNull Range<Integer> getCallingExpressionLines() {
      return myLines;
    }
  }
}
