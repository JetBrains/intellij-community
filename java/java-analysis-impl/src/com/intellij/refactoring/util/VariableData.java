/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariableData extends AbstractVariableData {
  public final PsiVariable variable;
  public PsiType type;

  public VariableData(@NotNull PsiVariable var) {
    variable = var;
    type = var.getType();
  }

  public VariableData(@Nullable PsiVariable var, PsiType type) {
    variable = var;
    if (var != null) {
      if (LambdaUtil.notInferredType(type)) {
        type = PsiType.getJavaLangObject(var.getManager(), GlobalSearchScope.allScope(var.getProject()));
      }
      this.type = SmartTypePointerManager.getInstance(var.getProject()).createSmartTypePointer(type).getType();
    }
    else {
      this.type = type;
    }
  }

  @NotNull
  public VariableData substitute(@Nullable PsiVariable var) {
    if (var == null) {
      return this;
    }
    PsiType type = JavaPsiFacade.getElementFactory(var.getProject()).createTypeFromText(this.type.getCanonicalText(), var);
    VariableData data = new VariableData(var, type);
    data.name = this.name;
    data.originalName = this.originalName;
    data.passAsParameter = this.passAsParameter;
    return data;
  }
}
