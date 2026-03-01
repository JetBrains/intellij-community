// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

public final class MethodNameMacro extends Macro {

  @Override
  public String getName() {
    return "methodName";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, final ExpressionContext context) {
    PsiElement elementAtStartOffset = context.getPsiElementAtStartOffset();
    PsiElement place = elementAtStartOffset;
    while(place != null){
      if (place instanceof PsiMethod method){
        return new TextResult(method.getName());
      } else if (place instanceof PsiClassInitializer initializer) {
        return initializer.hasModifierProperty(PsiModifier.STATIC) ?
               new TextResult(JavaBundle.message("java.terms.static.initializer")) :
               new TextResult(JavaBundle.message("java.terms.instance.initializer"));
      }
      place = place.getParent();
    }
    
    if (elementAtStartOffset != null) {
      PsiElement sibling = elementAtStartOffset.getNextSibling();
      if (sibling instanceof PsiMethod method) {
        return new TextResult(method.getName());
      }
    }
    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
