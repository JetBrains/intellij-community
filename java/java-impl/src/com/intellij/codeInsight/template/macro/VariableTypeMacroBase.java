// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class VariableTypeMacroBase extends Macro {
  protected abstract PsiElement @Nullable [] getVariables(Expression[] params, final ExpressionContext context);

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, final ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    Set<LookupElement> set = new LinkedHashSet<>();
    for (PsiElement element : vars) {
      JavaTemplateUtil.addElementLookupItem(set, element);
    }
    return set.toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new JavaPsiElementResult(vars[0]);
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "a";
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
