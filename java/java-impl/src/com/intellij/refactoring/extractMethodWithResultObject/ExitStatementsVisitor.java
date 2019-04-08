// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
abstract class ExitStatementsVisitor extends JavaRecursiveElementWalkingVisitor {
  private final Set<PsiElement> mySkippedContexts = new HashSet<>();
  private final PsiElement myTopmostElement;

  ExitStatementsVisitor(PsiElement topmostElement) {
    myTopmostElement = topmostElement;
  }

  protected abstract void processReferenceExpression(PsiReferenceExpression expression);

  protected abstract void processReturnExit(PsiReturnStatement statement);

  protected abstract void processContinueExit(PsiContinueStatement statement);

  protected abstract void processBreakExit(PsiBreakStatement statement);

  protected abstract void processThrowExit(PsiThrowStatement statement);

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);

    processReferenceExpression(expression);
  }

  @Override
  public void visitReturnStatement(PsiReturnStatement statement) {
    super.visitReturnStatement(statement);

    if (!isInSkippedContext(statement)) {
      processReturnExit(statement);
    }
  }

  @Override
  public void visitContinueStatement(PsiContinueStatement statement) {
    super.visitContinueStatement(statement);

    if (!isInSkippedContext(statement)) {
      processContinueExit(statement);
    }
  }

  @Override
  public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);

    if (!isInSkippedContext(statement)) {
      processBreakExit(statement);
    }
  }

  @Override
  public void visitThrowStatement(PsiThrowStatement statement) {
    super.visitThrowStatement(statement);

    if (!isInSkippedContext(statement)) {
      processThrowExit(statement);
    }
  }

  @Override
  public void visitClass(PsiClass aClass) {
    mySkippedContexts.add(aClass);
    super.visitClass(aClass);
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    mySkippedContexts.add(expression);
    super.visitLambdaExpression(expression);
  }

  private boolean isInSkippedContext(PsiElement element) {
    while (true) {
      if (element == myTopmostElement) {
        return false;
      }
      if (element == null || mySkippedContexts.contains(element)) {
        return true;
      }
      element = element.getContext();
    }
  }
}
