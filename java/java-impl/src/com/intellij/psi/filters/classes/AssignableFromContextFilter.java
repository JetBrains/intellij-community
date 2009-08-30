package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.reference.SoftReference;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 04.02.2003
 * Time: 12:40:51
 * To change this template use Options | File Templates.
 */
public class AssignableFromContextFilter implements ElementFilter{

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiClass.class, hintClass);
  }

  private SoftReference myCurrentContext = new SoftReference(null);
  private SoftReference myCachedClass = new SoftReference(null);
  public boolean isAcceptable(Object element, PsiElement context){
    if(myCurrentContext.get() != context){
      myCurrentContext = new SoftReference(context);
      PsiElement cachedClass = context;
      while(cachedClass != null && !(cachedClass instanceof PsiClass))
        cachedClass = cachedClass.getContext();
      myCachedClass = new SoftReference(cachedClass);
    }

    if(myCachedClass.get() instanceof PsiClass && element instanceof PsiClass){
      final String qualifiedName = ((PsiClass)myCachedClass.get()).getQualifiedName();
      return qualifiedName != null && (qualifiedName.equals(((PsiClass)element).getQualifiedName())
        || ((PsiClass)element).isInheritor((PsiClass)myCachedClass.get(), true));

    }
    return false;
  }

  public String toString(){
    return "assignable-from-context";
  }
}


