
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
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public class GuessElementTypeMacro extends Macro {
  @Override
  public String getName() {
    return "guessElementType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.guess.element.type.of.container");
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "A";
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, final ExpressionContext context) {
    PsiType[] types = guessTypes(params, context);
    if (types == null || types.length == 0) return null;
    return new PsiTypeResult(types[0], context.getProject());
  }

  @Override
  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    PsiType[] types = guessTypes(params, context);
    if (types == null || types.length < 2) return null;
    Set<LookupElement> set = new LinkedHashSet<>();
    for (PsiType type : types) {
      JavaTemplateUtil.addTypeLookupItem(set, type);
    }
    return set.toArray(new LookupElement[set.size()]);
  }

  @Nullable
  private static PsiType[] guessTypes(Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    if (expr == null) return null;
    PsiType[] types = GuessManager.getInstance(project).guessContainerElementType(expr, new TextRange(context.getTemplateStartOffset(), context.getTemplateEndOffset()));
    for (int i = 0; i < types.length; i++) {
      PsiType type = types[i];
      if (type instanceof PsiWildcardType) {
        if (((PsiWildcardType)type).isExtends()) {
          types[i] = ((PsiWildcardType)type).getBound();
        } else {
          types[i] = PsiType.getJavaLangObject(expr.getManager(), expr.getResolveScope());
        }
      }
    }
    return types;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}