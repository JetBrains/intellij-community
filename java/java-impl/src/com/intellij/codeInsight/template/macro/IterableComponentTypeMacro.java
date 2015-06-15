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
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class IterableComponentTypeMacro extends Macro {
  @Override
  public String getName() {
    return "iterableComponentType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.iterable.component.type");
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    if (expr == null) return null;
    PsiType type = expr.getType();


    if (type instanceof PsiArrayType) {
      return new PsiTypeResult(((PsiArrayType)type).getComponentType(), project);
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      PsiClass aClass = resolveResult.getElement();

      if (aClass != null) {
        PsiClass iterableClass = JavaPsiFacade.getInstance(project).findClass("java.lang.Iterable", aClass.getResolveScope());
        if (iterableClass != null) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(iterableClass, aClass, resolveResult.getSubstitutor());
          if (substitutor != null) {
            PsiType parameterType = substitutor.substitute(iterableClass.getTypeParameters()[0]);
            if (parameterType instanceof PsiCapturedWildcardType) {
              return new PsiTypeResult(((PsiCapturedWildcardType)parameterType).getUpperBound(), project);
            }
            if (parameterType != null) {
              if (parameterType instanceof PsiWildcardType) {
                if (((PsiWildcardType)parameterType).isExtends()) {
                  return new PsiTypeResult(((PsiWildcardType)parameterType).getBound(), project);
                }
                else return null;
              }
              return new PsiTypeResult(parameterType, project);
            }
          }
        }
      }
    }

    return null;
  }

  @Override
  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
