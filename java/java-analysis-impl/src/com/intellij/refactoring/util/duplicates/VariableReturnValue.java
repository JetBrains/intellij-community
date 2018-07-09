/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.util.duplicates;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */
public class VariableReturnValue implements ReturnValue {
  private final PsiVariable myVariable;

  public VariableReturnValue(PsiVariable variable) {
    myVariable = variable;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof VariableReturnValue)) return false;
    return myVariable == ((VariableReturnValue)other).myVariable;
  }

  public PsiVariable getVariable() {
    return myVariable;
  }

  @Nullable
  public PsiStatement createReplacement(@NotNull final PsiMethod extractedMethod, @NotNull final PsiMethodCallExpression methodCallExpression, @Nullable final PsiType returnType) throws IncorrectOperationException {
    final PsiDeclarationStatement statement;

    Project project = methodCallExpression.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
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
