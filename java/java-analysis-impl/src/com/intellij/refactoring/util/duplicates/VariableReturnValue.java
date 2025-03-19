// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariableReturnValue implements ReturnValue {
  private final PsiVariable myVariable;

  public VariableReturnValue(PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof VariableReturnValue)) return false;
    return myVariable == ((VariableReturnValue)other).myVariable;
  }

  public PsiVariable getVariable() {
    return myVariable;
  }

  @Override
  public @Nullable PsiStatement createReplacement(final @NotNull PsiMethod extractedMethod, final @NotNull PsiMethodCallExpression methodCallExpression, final @Nullable PsiType returnType) throws IncorrectOperationException {
    final PsiDeclarationStatement statement;

    Project project = methodCallExpression.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    JavaCodeStyleManager javaStyleManager = JavaCodeStyleManager.getInstance(project);
    PsiType type = returnType != null && returnType.isValid() ? returnType : myVariable.getType();
    statement = (PsiDeclarationStatement)styleManager.reformat(
      javaStyleManager.shortenClassReferences(
        elementFactory.createVariableDeclarationStatement(myVariable.getName(), type, methodCallExpression)));
    ((PsiVariable)statement.getDeclaredElements()[0]).getModifierList().replace(myVariable.getModifierList());
    return statement;
  }
}
