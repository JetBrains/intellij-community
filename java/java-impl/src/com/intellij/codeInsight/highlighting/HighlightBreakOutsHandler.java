// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.util.Consumer;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HighlightBreakOutsHandler extends HighlightUsagesHandlerBase<PsiElement> {
  private final PsiElement myTarget;

  public HighlightBreakOutsHandler(Editor editor, PsiFile file, PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  @Override
  public List<PsiElement> getTargets() {
    return Collections.singletonList(myTarget);
  }

  @Override
  protected void selectTargets(List<PsiElement> targets, Consumer<List<PsiElement>> selectionConsumer) {
    selectionConsumer.consume(targets);
  }

  @Override
  public void computeUsages(List<PsiElement> targets) {
    PsiElement parent = myTarget.getParent();
    if (parent instanceof PsiContinueStatement) {
      PsiStatement statement = ((PsiContinueStatement)parent).findContinuedStatement();
      if (statement instanceof PsiLoopStatement) {
        highlightLoopDeclaration((PsiLoopStatement)statement);
        PsiStatement body = ((PsiLoopStatement)statement).getBody();
        if (body instanceof PsiBlockStatement) {
          collectSiblings((PsiStatement)parent, statement, ((PsiBlockStatement)body).getCodeBlock());
        }
      }
    }
    else if (parent instanceof PsiBreakStatement) {
      PsiStatement exitedStatement = ((PsiBreakStatement)parent).findExitedStatement();
      if (exitedStatement instanceof PsiLoopStatement) {
        highlightLoopDeclaration((PsiLoopStatement)exitedStatement);
        PsiStatement body = ((PsiLoopStatement)exitedStatement).getBody();
        if (body instanceof PsiBlockStatement) {
          collectSiblings((PsiStatement)parent, exitedStatement, ((PsiBlockStatement)body).getCodeBlock());
        }
      }
      else if (exitedStatement instanceof PsiSwitchStatement) {
        addOccurrence(exitedStatement.getFirstChild());
        collectSiblings((PsiStatement)parent, exitedStatement, exitedStatement);
      }
    }
    addOccurrence(myTarget);
  }

  private void collectSiblings(PsiStatement currentStatement, PsiStatement container, @NotNull PsiElement block) {
    try {
      ControlFlow controlFlow =
        ControlFlowFactory.getInstance(block.getProject()).getControlFlow(block, new LocalsControlFlowPolicy(block), false, false);
      Collection<PsiStatement> statements = ControlFlowUtil
        .findExitPointsAndStatements(controlFlow, 0, controlFlow.getSize(), new IntArrayList(), ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
      for (PsiStatement psiStatement: statements) {
        if (currentStatement == psiStatement) continue;
        if (psiStatement instanceof PsiContinueStatement && ((PsiContinueStatement)psiStatement).findContinuedStatement() == container ||
            psiStatement instanceof PsiBreakStatement && ((PsiBreakStatement)psiStatement).findExitedStatement() == container) {
          addOccurrence(psiStatement.getFirstChild());
        }
      }
    }
    catch (AnalysisCanceledException ignored) { }
  }

  private void highlightLoopDeclaration(PsiLoopStatement statement) {
    
    if (statement instanceof PsiDoWhileStatement) {
      PsiKeyword whileKeyword = ((PsiDoWhileStatement)statement).getWhileKeyword();
      if (whileKeyword != null) {
        addOccurrence(whileKeyword);
      }
    }
    else {
      addOccurrence(statement.getFirstChild());
    }
  }
}
