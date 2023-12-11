// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
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
  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, final ExpressionContext context) {
    PsiElement elementAtStartOffset = context.getPsiElementAtStartOffset();
    PsiElement place = elementAtStartOffset;
    while(place != null){
      if (place instanceof PsiMethod){
        return new TextResult(((PsiMethod)place).getName());
      } else if (place instanceof PsiClassInitializer) {
        return ((PsiClassInitializer) place).hasModifierProperty(PsiModifier.STATIC) ?
               new TextResult(JavaBundle.message("java.terms.static.initializer")) :
               new TextResult(JavaBundle.message("java.terms.instance.initializer"));
      }
      place = place.getParent();
    }
    
    if (elementAtStartOffset != null) {
      PsiElement sibling = elementAtStartOffset.getNextSibling();
      if (sibling instanceof PsiMethod) {
        return new TextResult(((PsiMethod)sibling).getName());
      }
    }
    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
