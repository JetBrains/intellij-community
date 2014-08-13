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
package com.intellij.psi.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 17:45:45
 * To change this template use Options | File Templates.
 */
public class FilterUtil{
  private FilterUtil() {
  }

  @Nullable
  public static PsiType getTypeByElement(PsiElement element, PsiElement context){
    //if(!element.isValid()) return null;
    if(element instanceof PsiType){
      return (PsiType)element;
    }
    if(element instanceof PsiClass){
      return JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createType((PsiClass)element);
    }
    if(element instanceof PsiMethod){
      if (((PsiMethod)element).isConstructor()) {
        final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
        if (containingClass != null) {
          return JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createType(containingClass);
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
    if(PsiKeyword.CLASS.equals(keyword)){
      return PsiType.getJavaLangClass(context.getManager(), context.getResolveScope());
    }
    else if(PsiKeyword.TRUE.equals(keyword) || PsiKeyword.FALSE.equals(keyword)){
      return PsiType.BOOLEAN;
    }
    else if(PsiKeyword.THIS.equals(keyword)){
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

  @Nullable
  public static PsiElement getPreviousElement(final PsiElement element, boolean skipReference){
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
