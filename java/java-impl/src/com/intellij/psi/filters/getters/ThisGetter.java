// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters.getters;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public final class ThisGetter {

  public static List<PsiExpression> getThisExpressionVariants(PsiElement context) {
    boolean first = true;
    final List<PsiExpression> expressions = new ArrayList<>();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());

    PsiElement prev = context;
    context = context.getContext();

    while(context != null){
      if(context instanceof PsiClass && !(prev instanceof PsiExpressionList)){
        final String expressionText;
        if(first){
          first = false;
          expressionText = PsiKeyword.THIS;
        }
        else expressionText = ((PsiClass)context).getName() + "." + PsiKeyword.THIS;
        try{
          expressions.add(factory.createExpressionFromText(expressionText, context));
        }
        catch(IncorrectOperationException ioe){}
      }
      if(context instanceof PsiModifierListOwner){
        if(((PsiModifierListOwner)context).hasModifierProperty(PsiModifier.STATIC)) break;
      }
      prev = context;
      context = context.getContext();
    }
    return expressions;
  }
}
