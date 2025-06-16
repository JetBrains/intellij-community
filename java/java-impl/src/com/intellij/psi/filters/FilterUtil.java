// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public final class FilterUtil{
  private FilterUtil() {
  }

  public static @Nullable PsiType getTypeByElement(PsiElement element, PsiElement context){
    //if(!element.isValid()) return null;
    if(element instanceof PsiType){
      return (PsiType)element;
    }
    if(element instanceof PsiClass){
      return JavaPsiFacade.getElementFactory(element.getProject()).createType((PsiClass)element);
    }
    if(element instanceof PsiMethod){
      if (((PsiMethod)element).isConstructor()) {
        final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
        if (containingClass != null) {
          return JavaPsiFacade.getElementFactory(element.getProject()).createType(containingClass);
        }
      }
      return ((PsiMethod)element).getReturnType();
    }
    if(element instanceof PsiVariable){
      return ((PsiVariable)element).getType();
    }
    if(element instanceof PsiKeyword){
      return getKeywordItemType(context, element.getText());
    }
    if(element instanceof PsiExpression){
      return ((PsiExpression)element).getType();
    }

    return null;
  }

  public static PsiType getKeywordItemType(PsiElement context, final String keyword) {
    if(JavaKeywords.CLASS.equals(keyword)){
      return PsiType.getJavaLangClass(context.getManager(), context.getResolveScope());
    }
    else if(JavaKeywords.TRUE.equals(keyword) || JavaKeywords.FALSE.equals(keyword)){
      return PsiTypes.booleanType();
    }
    else if(JavaKeywords.THIS.equals(keyword)){
      PsiElement previousElement = getPreviousElement(context, false);
      if(previousElement != null && ".".equals(previousElement.getText())){
        previousElement = getPreviousElement(previousElement, false);
        assert previousElement != null;

        final String className = previousElement.getText();
        PsiElement walker = context;
        while(walker != null){
          if(walker instanceof PsiClass && !(walker instanceof PsiAnonymousClass)){
            if(className.equals(((PsiClass)walker).getName()))
              return getTypeByElement(walker, context);
          }
          walker = walker.getContext();
        }
      }
      else{
        final PsiClass owner = PsiTreeUtil.getContextOfType(context, PsiClass.class, true);
        return getTypeByElement(owner, context);
      }
    }
    return null;
  }

  public static @Nullable PsiElement getPreviousElement(final PsiElement element, boolean skipReference){
    PsiElement prev = element;
    if(element != null){
      if(skipReference){
        prev = FilterPositionUtil.searchNonSpaceNonCommentBack(element);
        while(prev != null && prev.getParent() instanceof PsiJavaCodeReferenceElement){
          prev = FilterPositionUtil.searchNonSpaceNonCommentBack(prev.getParent());
        }
      }
      else{
        prev = FilterPositionUtil.searchNonSpaceNonCommentBack(prev);
      }
    }
    return prev;
  }
}
