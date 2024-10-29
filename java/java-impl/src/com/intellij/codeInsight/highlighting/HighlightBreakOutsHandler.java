// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.util.Consumer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class HighlightBreakOutsHandler extends HighlightUsagesHandlerBase<PsiElement> implements DumbAware {
  private final PsiElement myTarget;

  public HighlightBreakOutsHandler(Editor editor, PsiFile file, PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  @Override
  public @NotNull List<PsiElement> getTargets() {
    return Collections.singletonList(myTarget);
  }

  @Override
  protected void selectTargets(@NotNull List<? extends PsiElement> targets, @NotNull Consumer<? super List<? extends PsiElement>> selectionConsumer) {
    selectionConsumer.consume(targets);
  }

  @Override
  public void computeUsages(@NotNull List<? extends PsiElement> targets) {
    PsiElement parent = myTarget.getParent();
    if (parent instanceof PsiContinueStatement) {
      PsiElement statement = ((PsiContinueStatement)parent).findContinuedStatement();
      if (statement instanceof PsiLoopStatement) {
        processLoop((PsiStatement)parent, (PsiLoopStatement)statement);
      }
    }
    else if (parent instanceof PsiBreakStatement) {
      PsiStatement exitedStatement = ((PsiBreakStatement)parent).findExitedStatement();
      if (exitedStatement instanceof PsiLoopStatement) {
        processLoop((PsiStatement)parent, (PsiLoopStatement)exitedStatement);
      }
      else if (exitedStatement instanceof PsiSwitchStatement) {
        addOccurrence(exitedStatement.getFirstChild());
        collectSiblings((PsiStatement)parent, exitedStatement, exitedStatement);
      }
    }
    else if (parent instanceof PsiYieldStatement) {
      PsiSwitchExpression enclosingExpression = ((PsiYieldStatement)parent).findEnclosingExpression();
      if (enclosingExpression != null) {
        addOccurrence(enclosingExpression.getFirstChild());
        collectSiblings((PsiStatement)parent, enclosingExpression, enclosingExpression);
      }
    }
    addOccurrence(myTarget);
  }

  private void processLoop(PsiStatement parent, PsiLoopStatement statement) {
    highlightLoopDeclaration(statement);
    PsiStatement body = statement.getBody();
    if (body instanceof PsiBlockStatement) {
      collectSiblings(parent, statement, ((PsiBlockStatement)body).getCodeBlock());
    }
  }

  private void collectSiblings(PsiStatement currentStatement, PsiElement container, @NotNull PsiElement block) {
    try {
      ControlFlow controlFlow =
        ControlFlowFactory.getControlFlow(block, new LocalsControlFlowPolicy(block), ControlFlowOptions.NO_CONST_EVALUATE);
      Collection<PsiStatement> statements = ControlFlowUtil
        .findExitPointsAndStatements(controlFlow, 0, controlFlow.getSize(), new IntArrayList(), ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
      for (PsiStatement psiStatement: statements) {
        if (currentStatement == psiStatement) continue;
        if (psiStatement instanceof PsiContinueStatement && ((PsiContinueStatement)psiStatement).findContinuedStatement() == container ||
            psiStatement instanceof PsiBreakStatement && ((PsiBreakStatement)psiStatement).findExitedStatement() == container ||
            psiStatement instanceof PsiYieldStatement && ((PsiYieldStatement)psiStatement).findEnclosingExpression() == container) {
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
