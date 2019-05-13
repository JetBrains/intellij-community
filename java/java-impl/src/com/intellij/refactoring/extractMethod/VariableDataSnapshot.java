// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.AbstractVariableData;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class VariableDataSnapshot extends AbstractVariableData {
  @Nullable final SmartPsiElementPointer<PsiVariable> myVariable;
  @Nullable final SmartTypePointer myType;

  public VariableDataSnapshot(@NotNull VariableData data, @NotNull Project project) {
    this(data.variable, data.type, data.name, data.originalName, data.passAsParameter, project);
  }

  public VariableDataSnapshot(@Nullable PsiVariable variable, @Nullable PsiType type,
                              String name, String originalName, boolean passAsParameter,
                              @NotNull Project project) {
    myVariable = variable != null ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer(variable) : null;
    myType = type != null ? SmartTypePointerManager.getInstance(project).createSmartTypePointer(type) : null;
    this.name = name;
    this.originalName = originalName;
    this.passAsParameter = passAsParameter;
  }

  @Nullable
  public VariableData getData() {
    PsiVariable variable = getVariable();
    if (variable != null) {
      PsiType type = getType();
      VariableData data = new VariableData(variable, type);
      data.name = this.name;
      data.originalName = this.originalName;
      data.passAsParameter = this.passAsParameter;
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
