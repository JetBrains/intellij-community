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
package com.intellij.psi.filters.getters;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public class ThisGetter {

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
