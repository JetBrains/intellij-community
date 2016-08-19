/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  SoftReference<PsiElement> myCachedVar = new SoftReference<>(null);
  SoftReference<PsiElement> myCurrentContext = new SoftReference<>(null);

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    PsiElement cachedVar = context;

    if(myCurrentContext.get() != context){
      myCurrentContext = new SoftReference<>(context);
      while(cachedVar != null && !(getFilter().isAcceptable(cachedVar, cachedVar.getContext())))
        cachedVar = cachedVar.getContext();
      myCachedVar = new SoftReference<>(cachedVar);
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

  //TODO check exotic conditions like overriding method in package-private class from class in other package
  private static boolean isOverridingMethod(final PsiMethod method, final PsiMethod candidate) {
    if (method.getManager().areElementsEquivalent(method, candidate)) return false;
    if (!MethodSignatureUtil.areSignaturesEqual(method,candidate)) return false;
    final PsiClass candidateContainingClass = candidate.getContainingClass();
    return candidateContainingClass.isInheritor(method.getContainingClass(), true);
  }
}
