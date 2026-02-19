// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class TypeParameterOfMacro extends Macro {
  @Override
  public String getName() {
    return "typeParameterOf";
  }

  @Override
  public String getPresentableName() {
    return getName() + "(VAR[,indexOrName])";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "A";
  }

  @Override
  public @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    if (params.length == 0) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) {
      return null;
    }

    final PsiType type;
    if (result instanceof PsiTypeResult typeResult) {
      type = typeResult.getType();
    }
    else {
      final PsiExpression expression = MacroUtil.resultToPsiExpression(result, context);
      if (expression == null) {
        return null;
      }
      type = expression.getType();
    }
    if (type instanceof PsiClassType classType) {
      final PsiType[] parameters = classType.getParameters();
      if (params.length > 1) {
        final Result result1 = params[1].calculateResult(context);
        if (result1 == null) {
          return null;
        }
        final String value = result1.toString();
        try {
          final int i = Integer.parseInt(value);
          return new PsiTypeResult(parameters[i], context.getProject());
        }
        catch (ArrayIndexOutOfBoundsException e) {
          return null;
        }
        catch (NumberFormatException e) {
          final PsiClass aClass = PsiTypesUtil.getPsiClass(classType);
          if (aClass == null) return null;
          PsiTypeParameter @NotNull [] typeParameters = aClass.getTypeParameters();
          for (int i = 0; i < typeParameters.length; i++) {
            PsiTypeParameter parameter = typeParameters[i];
            if (value.equals(parameter.getName())) {
              return new PsiTypeResult(parameters[i], context.getProject());
            }
          }
          return null;
        }
      }
      else {
        return new PsiTypeResult(parameters[0], context.getProject());
      }
    }
    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}
