// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariableData extends AbstractVariableData {
  public final PsiVariable variable;
  public PsiType type;

  public VariableData(@NotNull PsiVariable var) {
    variable = var;
    type = correctType(var.getType()); 
  }

  public VariableData(@Nullable PsiVariable var, PsiType type) {
    variable = var;
    if (var != null) {
      if (LambdaUtil.notInferredType(type)) {
        type = PsiType.getJavaLangObject(var.getManager(), GlobalSearchScope.allScope(var.getProject()));
      }
      this.type = correctType(SmartTypePointerManager.getInstance(var.getProject()).createSmartTypePointer(type).getType());
    }
    else {
      this.type = correctType(type);
    }
  }

  private static PsiType correctType(PsiType varType) {
    if (varType instanceof PsiDisjunctionType) {
      return PsiTypesUtil.getLowestUpperBoundClassType((PsiDisjunctionType)varType);
    }
    return varType;
  }

  public @NotNull VariableData substitute(@Nullable PsiVariable var) {
    if (var == null) {
      return this;
    }
    // The copied type needs to be valid in a non-physical copy of the original file.
    // If the type references a type variable declared in the original file, it might not work in the copy.
    PsiType type = this.type instanceof PsiImmediateClassType && ((PsiImmediateClassType)this.type).resolve() instanceof PsiTypeParameter
                   ? JavaPsiFacade.getElementFactory(var.getProject()).createTypeFromText(this.type.getCanonicalText(), var)
                   : this.type;
    VariableData data = new VariableData(var, type);
    data.name = this.name;
    data.originalName = this.originalName;
    data.passAsParameter = this.passAsParameter;
    return data;
  }
}
