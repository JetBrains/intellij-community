// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ReturnStatementsVisitor;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class ConvertReturnStatementsVisitor implements ReturnStatementsVisitor {
  private final @NotNull PsiElementFactory myFactory;
  private final @NotNull PsiMethod myMethod;
  private final @NotNull DeclarationSearcher mySearcher;
  private final @NotNull String myDefaultValue;
  private PsiReturnStatement myLatestReturn;

  ConvertReturnStatementsVisitor(@NotNull PsiElementFactory factory, @NotNull PsiMethod method, @NotNull PsiType targetType) {
    myFactory = factory;
    myMethod = method;
    mySearcher = new DeclarationSearcher(method, targetType);
    myDefaultValue = PsiTypesUtil.getDefaultValueOfType(targetType);
  }

  @Override
  public void visit(final List<PsiReturnStatement> returnStatements) throws IncorrectOperationException {
    final PsiReturnStatement statement = myMethod.isPhysical()
                                         ? WriteAction.compute(() -> replaceReturnStatements(returnStatements))
                                         : replaceReturnStatements(returnStatements);
    if (statement != null) {
      myLatestReturn = statement;
    }
  }

  public PsiReturnStatement getLatestReturn() {
    return myLatestReturn;
  }

  private String generateValue(@NotNull PsiElement stopElement) {
    final PsiVariable variable = mySearcher.getDeclaration(stopElement);
    return variable != null ? variable.getName() : myDefaultValue;
  }

  public PsiReturnStatement createReturnInLastStatement() throws IncorrectOperationException {
    ThrowableComputable<PsiReturnStatement, RuntimeException> action = () -> {
      PsiCodeBlock body = myMethod.getBody();
      PsiJavaToken rBrace = body.getRBrace();
      if (rBrace == null) return null;
      final String value = generateValue(rBrace);
      PsiReturnStatement returnStatement = (PsiReturnStatement)myFactory.createStatementFromText("return " + value + ";", myMethod);
      return (PsiReturnStatement)body.addBefore(returnStatement, rBrace);
    };
    return myMethod.isPhysical() ? WriteAction.compute(action) : action.compute();
  }

  public @Nullable PsiReturnStatement replaceReturnStatements(final List<? extends PsiReturnStatement> currentStatements) throws IncorrectOperationException {
    PsiReturnStatement latestReplaced = null;

    for (PsiReturnStatement returnStatement : currentStatements) {
      if (returnStatement.getReturnValue() != null) {
        continue;
      }
      final String value = generateValue(returnStatement);

      latestReplaced = (PsiReturnStatement) myFactory.createStatementFromText("return " + value+";", returnStatement.getParent());
      latestReplaced = (PsiReturnStatement)returnStatement.replace(latestReplaced);
    }

    return latestReplaced;
  }
}
