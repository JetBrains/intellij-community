// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class AnonymousClassVariableHidesOuterClassVariableVisitor extends BaseInspectionVisitor {
  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
    super.visitAnonymousClass(aClass);
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(aClass, PsiCodeBlock.class);
    if (codeBlock == null) {
      return;
    }
    final VariableCollector collector = new VariableCollector();
    aClass.acceptChildren(collector);
    final PsiStatement[] statements = codeBlock.getStatements();
    final int offset = aClass.getTextOffset();
    for (PsiStatement statement : statements) {
      if (statement.getTextOffset() >= offset) {
        break;
      }
      if (!(statement instanceof PsiDeclarationStatement declarationStatement)) {
        continue;
      }
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable localVariable)) {
          continue;
        }
        final String name = localVariable.getName();
        final PsiVariable[] variables = collector.getVariables(name);
        for (PsiVariable variable : variables) {
          registerVariableError(variable, variable);
        }
      }
    }
    final PsiElement containingMethod = PsiTreeUtil.getParentOfType(codeBlock, PsiMethod.class, PsiLambdaExpression.class);
    if (containingMethod == null) {
      return;
    }

    final PsiParameterList parameterList = containingMethod instanceof PsiMethod ? ((PsiMethod)containingMethod).getParameterList() 
                                                                                 : ((PsiLambdaExpression)containingMethod).getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      final String name = parameter.getName();
      final PsiVariable[] variables = collector.getVariables(name);
      for (PsiVariable variable : variables) {
        registerVariableError(variable, variable);
      }
    }
  }

  private static final class VariableCollector extends JavaRecursiveElementWalkingVisitor {
    private static final PsiVariable[] EMPTY_VARIABLE_LIST = {};

    private final Map<String, List<PsiVariable>> variableMap = new HashMap<>();

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final String name = variable.getName();
      final List<PsiVariable> variableList = variableMap.get(name);
      if (variableList == null) {
        final List<PsiVariable> list = new ArrayList<>();
        list.add(variable);
        variableMap.put(name, list);
      }
      else {
        variableList.add(variable);
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // don't drill down in classes
    }

    public PsiVariable @NotNull [] getVariables(String name) {
      final List<PsiVariable> variableList = variableMap.get(name);
      if (variableList == null) {
        return EMPTY_VARIABLE_LIST;
      }
      else {
        return variableList.toArray(new PsiVariable[0]);
      }
    }
  }
}
