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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class VariableOfTypeMacro implements Macro {

  public String getName() {
    return "variableOfType";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.variable.of.type");
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new JavaPsiElementResult(vars[0]);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    final Set<LookupElement> set = new LinkedHashSet<LookupElement>();
    for (PsiElement var : vars) {
      JavaTemplateUtil.addElementLookupItem(set, var);
    }
    return set.toArray(new LookupElement[set.size()]);
  }

  @Nullable
  private static PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();
    final int offset = context.getStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    PsiType type = MacroUtil.resultToPsiType(result, context);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);

    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    PsiManager manager = PsiManager.getInstance(project);
    for (PsiVariable var : variables) {
      if (var instanceof PsiField && var.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass varClass = ((PsiField)var).getContainingClass();
        PsiClass placeClass = PsiTreeUtil.getParentOfType(place, PsiClass.class);
        if (!manager.areElementsEquivalent(varClass, placeClass)) continue;
      }
      else if (var instanceof PsiLocalVariable) {
        final TextRange range = var.getNameIdentifier().getTextRange();
        if (range != null && range.contains(offset)) {
          continue;
        }
      }

      PsiType type1 = var.getType();
      if (type == null || type.isAssignableFrom(type1)) {
        array.add(var);
      }
    }

    PsiExpression[] expressions = MacroUtil.getStandardExpressionsOfType(place, type);
    ContainerUtil.addAll(array, expressions);
    return array.toArray(new PsiElement[array.size()]);
  }
}

