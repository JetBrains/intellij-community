// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LocalVariableDeclarationDescriptor implements ItemToReplaceDescriptor {
  private final List<AccessibleLocalVariable> myVariables;
  private final PsiDeclarationStatement myDeclarationStatement;

  public LocalVariableDeclarationDescriptor(@NotNull List<AccessibleLocalVariable> names, PsiDeclarationStatement statement) {
    myVariables = names;
    myDeclarationStatement = statement;
  }

  @Nullable
  public static ItemToReplaceDescriptor createIfInaccessible(@NotNull PsiDeclarationStatement declarationStatement) {
    List<AccessibleLocalVariable> variables = new ArrayList<>();
    for (PsiElement element : declarationStatement.getDeclaredElements()) {
      if (!(element instanceof PsiLocalVariable)) {
        return null;
      }

      PsiLocalVariable variable = (PsiLocalVariable)element;
      String name = variable.getName();
      PsiType variableType = variable.getType();
      if (!PsiReflectionAccessUtil.isAccessibleType(variableType)) {
        variables.add(new AccessibleLocalVariable(name, PsiReflectionAccessUtil.nearestAccessibleType(variableType), variable));
      }
    }

    if (!variables.isEmpty()) {
      return new LocalVariableDeclarationDescriptor(variables, declarationStatement);
    }
    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    AccessibleLocalVariable variable = myVariables.get(0);
    PsiDeclarationStatement lastDeclaration = variable.toVariableDeclaration(elementFactory);
    myDeclarationStatement.replace(lastDeclaration);

    for (int i = 1; i < myVariables.size(); i++) {
      variable = myVariables.get(i);
      PsiDeclarationStatement declaration = variable.toVariableDeclaration(elementFactory);
      lastDeclaration.getParent().addAfter(declaration, lastDeclaration);
      lastDeclaration = declaration;
    }
  }

  private static final class AccessibleLocalVariable {
    private final String myName;
    private final PsiType myAccessibleType;
    private final PsiLocalVariable myLocalVariable;

    private AccessibleLocalVariable(@NotNull String name, @NotNull PsiType type, @NotNull PsiLocalVariable localVariable) {
      myName = name;
      myAccessibleType = type;
      myLocalVariable = localVariable;
    }

    private PsiDeclarationStatement toVariableDeclaration(@NotNull PsiElementFactory elementFactory) {
      return elementFactory.createVariableDeclarationStatement(myName, myAccessibleType, myLocalVariable.getInitializer(), myLocalVariable);
    }
  }
}
