package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiReturnStatement;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public interface ReturnStatementsVisitor {
  void visit(final List<PsiReturnStatement> returnStatements) throws IncorrectOperationException;
}
