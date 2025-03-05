// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.AbstractVariableData;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class VariableDataSnapshot extends AbstractVariableData {
  private final @Nullable SmartPsiElementPointer<PsiVariable> myVariable;
  private final @Nullable SmartTypePointer myType;

  VariableDataSnapshot(@NotNull VariableData data, @NotNull Project project) {
    this(data.variable, data.type, data.name, data.originalName, data.passAsParameter, project);
  }

  VariableDataSnapshot(@Nullable PsiVariable variable, @Nullable PsiType type,
                       String name, String originalName, boolean passAsParameter,
                       @NotNull Project project) {
    myVariable = variable != null ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer(variable) : null;
    myType = type != null ? SmartTypePointerManager.getInstance(project).createSmartTypePointer(type) : null;
    this.name = name;
    this.originalName = originalName;
    this.passAsParameter = passAsParameter;
  }

  public @Nullable VariableData getData() {
    PsiVariable variable = getVariable();
    if (variable != null) {
      PsiType type = getType();
      VariableData data = new VariableData(variable, type);
      data.name = name;
      data.originalName = originalName;
      data.passAsParameter = passAsParameter;
      return data;
    }
    return null;
  }

  @Nullable
  PsiVariable getVariable() {
    return myVariable != null ? myVariable.getElement() : null;
  }

  @Nullable
  PsiType getType() {
    return myType != null ? myType.getType() : null;
  }
}
