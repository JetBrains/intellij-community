/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class SuggestFirstVariableNameMacro extends VariableOfTypeMacro {
  @Override
  public String getName() {
    return "suggestFirstVariableName";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.suggest.first.variable.name");
  }

  @Override
  protected PsiElement[] getVariables(Expression[] params, ExpressionContext context) {
    final PsiElement[] variables = super.getVariables(params, context);
    if (variables == null) return null;
    final List<PsiElement> result = new ArrayList<>();
    final List<String> skip = Arrays.asList("true", "false", "this", "super");
    for (PsiElement variable : variables) {
      if (!skip.contains(variable.getText())) {
        result.add(variable);
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}


