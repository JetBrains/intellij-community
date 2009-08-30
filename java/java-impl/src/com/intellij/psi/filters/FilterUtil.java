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
    else if(element instanceof PsiMethod){
      return ((PsiMethod)element).getReturnType();
    }
    else if(element instanceof PsiVariable){
      return ((PsiVariable)element).getType();
    }
    else if(element instanceof PsiKeyword){
      if(PsiKeyword.CLASS.equals(element.getText())){
        return PsiType.getJavaLangClass(element.getManager(), element.getResolveScope());
      }
      else if(PsiKeyword.TRUE.equals(element.getText()) || PsiKeyword.FALSE.equals(element.getText())){
        return PsiType.BOOLEAN;
      }
      else if(PsiKeyword.THIS.equals(element.getText())){
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
    }
    else if(element instanceof PsiExpression){
      return ((PsiExpression)element).getType();
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
