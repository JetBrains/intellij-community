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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class IterableVariableMacro extends VariableTypeMacroBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.macro.IterableVariableMacro");

  @Override
  public String getName() {
    return "iterableVariable";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.iterable.variable");
  }

  @Override
  @Nullable
  protected PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    final List<PsiElement> result = new ArrayList<>();


    Project project = context.getProject();
    final int offset = context.getStartOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    assert file != null;
    PsiElement place = file.findElementAt(offset);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final GlobalSearchScope scope = file.getResolveScope();

    PsiType iterableType = elementFactory.createTypeByFQClassName("java.lang.Iterable", scope);
    PsiType mapType = elementFactory.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, scope);

    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    for (PsiVariable var : variables) {
      final PsiElement parent = var.getParent();
      if (parent instanceof PsiForeachStatement && parent == PsiTreeUtil.getParentOfType(place, PsiForeachStatement.class)) continue;

      PsiType type = VariableTypeCalculator.getVarTypeAt(var, place);
      if (type instanceof PsiArrayType || iterableType.isAssignableFrom(type)) {
        result.add(var);
      }
      else if (mapType.isAssignableFrom(type)) {
        try {
          result.add(elementFactory.createExpressionFromText(var.getName() + ".keySet()", var.getParent()));
          result.add(elementFactory.createExpressionFromText(var.getName() + ".values()", var.getParent()));
          result.add(elementFactory.createExpressionFromText(var.getName() + ".entrySet()", var.getParent()));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}
