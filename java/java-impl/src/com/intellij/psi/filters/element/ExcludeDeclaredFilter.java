package com.intellij.psi.filters.element;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.PositionElementFilter;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.reference.SoftReference;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 26.02.2003
 * Time: 12:31:24
 * To change this template use Options | File Templates.
 */
public class ExcludeDeclaredFilter extends PositionElementFilter{
  public ExcludeDeclaredFilter(ElementFilter filter){
    setFilter(filter);
  }

  public boolean isClassAcceptable(Class hintClass){
    return true;
    //return PsiVariable.class.isAssignableFrom(hintClass);
  }

  SoftReference<PsiElement> myCachedVar = new SoftReference<PsiElement>(null);
  SoftReference<PsiElement> myCurrentContext = new SoftReference<PsiElement>(null);

  public boolean isAcceptable(Object element, PsiElement context){
    PsiElement cachedVar = context;

    if(myCurrentContext.get() != context){
      myCurrentContext = new SoftReference<PsiElement>(context);
      while(cachedVar != null && !(getFilter().isAcceptable(cachedVar, cachedVar.getContext())))
        cachedVar = cachedVar.getContext();
      myCachedVar = new SoftReference<PsiElement>(cachedVar);
    }

    if (element instanceof PsiMethod && myCachedVar.get() instanceof PsiMethod)  {
      final PsiMethod currentMethod = (PsiMethod) element;
      final PsiMethod candidate = (PsiMethod) myCachedVar.get();
      return !candidate.getManager().areElementsEquivalent(candidate, currentMethod) && !isOverridingMethod(currentMethod, candidate);
    }
    else if(element instanceof PsiClassType){
      final PsiClass psiClass = ((PsiClassType)element).resolve();
      return isAcceptable(psiClass, context);
    }
    else if(context != null){
      if(element instanceof PsiElement)
        return !context.getManager().areElementsEquivalent(myCachedVar.get(), (PsiElement)element);
      return true;
    }
    return true;
  }

  //TODO check exotic conditions like overriding method in package local class from class in other package
  private static boolean isOverridingMethod(final PsiMethod method, final PsiMethod candidate) {
    if (method.getManager().areElementsEquivalent(method, candidate)) return false;
    if (!MethodSignatureUtil.areSignaturesEqual(method,candidate)) return false;
    final PsiClass candidateContainingClass = candidate.getContainingClass();
    return candidateContainingClass.isInheritor(method.getContainingClass(), true);
  }
}
