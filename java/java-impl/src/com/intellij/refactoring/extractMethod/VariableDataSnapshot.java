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
  final SmartPsiElementPointer<PsiVariable> myVariable;
  final SmartTypePointer myType;

  public VariableDataSnapshot(@NotNull VariableData data, @NotNull Project project) {
    myVariable = data.variable != null ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer(data.variable) : null;
    myType = data.type != null ? SmartTypePointerManager.getInstance(project).createSmartTypePointer(data.type) : null;
    this.name = data.name;
    this.originalName = data.originalName;
    this.passAsParameter = data.passAsParameter;
  }

  @Nullable
  public VariableData getData() {
    PsiVariable variable = myVariable.getElement();
    if (variable != null) {
      PsiType type = myType.getType();
      VariableData data = new VariableData(variable, type);
      data.name = this.name;
      data.originalName = this.originalName;
      data.passAsParameter = this.passAsParameter;
      return data;
    }
    return null;
  }
}
