// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ControlFlowStatementVisitorBase extends BaseInspectionVisitor {

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, JavaKeywords.FOR);
    }
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    super.visitForStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, JavaKeywords.FOR);
    }
  }


  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    super.visitWhileStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, JavaKeywords.WHILE);
    }
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    super.visitDoWhileStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, JavaKeywords.DO);
    }
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    super.visitIfStatement(statement);
    final PsiStatement thenBranch = statement.getThenBranch();
    if (isApplicable(thenBranch)) {
      registerControlFlowStatementErrors(statement.getFirstChild(), thenBranch.getLastChild(), thenBranch, JavaKeywords.IF);
    }
    final PsiStatement elseBranch = statement.getElseBranch();
    if (isApplicable(elseBranch)) {
      registerControlFlowStatementErrors(statement.getElseElement(), elseBranch.getLastChild(), elseBranch, JavaKeywords.ELSE);
    }
  }

  @Contract("null->false")
  protected abstract boolean isApplicable(PsiStatement body);

  protected abstract @Nullable Pair<PsiElement, PsiElement> getOmittedBodyBounds(PsiStatement body);

  private void registerLoopStatementErrors(@NotNull PsiLoopStatement statement, @NotNull PsiStatement body, @NotNull String keywordText) {
    registerControlFlowStatementErrors(statement.getFirstChild(), statement.getLastChild(), body, keywordText);
  }

  private void registerControlFlowStatementErrors(@Nullable PsiElement rangeStart,
                                                  @Nullable PsiElement rangeEnd,
                                                  @NotNull PsiStatement body,
                                                  @NotNull String keywordText) {
    boolean highlightOnlyKeyword = isVisibleHighlight(body);
    if (highlightOnlyKeyword) {
      if (rangeStart != null) {
        registerError(rangeStart, keywordText);
      }
      return;
    }

    final Pair<PsiElement, PsiElement> omittedBodyBounds = getOmittedBodyBounds(body);
    if (omittedBodyBounds == null) {
      if (rangeStart != null && rangeEnd != null) {
        registerErrorAtRange(rangeStart, rangeEnd, keywordText);
      }
      return;
    }

    if (rangeStart != null) {
      PsiElement parent = PsiTreeUtil.findCommonParent(rangeStart, omittedBodyBounds.getFirst());
      if (parent != null) {
        int parentStart = parent.getTextRange().getStartOffset();
        int startOffset = rangeStart.getTextRange().getStartOffset();
        int length = omittedBodyBounds.getFirst().getTextRange().getStartOffset() - startOffset;
        if (length > 0) {
          registerErrorAtOffset(parent, startOffset - parentStart, length, keywordText);
        }
      }
    }

    final PsiElement afterOmitted = omittedBodyBounds.getSecond();
    if (afterOmitted != null) {
      if (rangeEnd != null && rangeEnd != afterOmitted) {
        PsiElement parent = PsiTreeUtil.findCommonParent(rangeEnd, afterOmitted);
        if (parent != null) {
          int parentStart = parent.getTextRange().getStartOffset();
          int startOffset = afterOmitted.getTextRange().getEndOffset();
          int length = rangeEnd.getTextRange().getEndOffset() - startOffset;
          if (length > 0) {
            registerErrorAtOffset(parent, startOffset - parentStart, length,
                                  keywordText);
          }
        }
      }
    }
  }
}