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
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.ArrayList;

public class ArrayVariableMacro extends VariableTypeMacroBase {
  @Override
  public String getName() {
    return "arrayVariable";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.array.variable");
  }

  @Override
  protected PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    Project project = context.getProject();
    final int offset = context.getStartOffset();
    final ArrayList<PsiVariable> array = new ArrayList<>();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    for (PsiVariable variable : variables) {
      PsiType type = VariableTypeCalculator.getVarTypeAt(variable, place);
      if (type instanceof PsiArrayType) {
        array.add(variable);
      }
    }
    return array.toArray(new PsiVariable[array.size()]);
  }
}
