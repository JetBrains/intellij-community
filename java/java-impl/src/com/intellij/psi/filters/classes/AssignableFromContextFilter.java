/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.reference.SoftReference;
import com.intellij.util.ReflectionUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 04.02.2003
 * Time: 12:40:51
 * To change this template use Options | File Templates.
 */
public class AssignableFromContextFilter implements ElementFilter{

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionUtil.isAssignable(PsiClass.class, hintClass);
  }

  private SoftReference myCurrentContext = new SoftReference(null);
  private SoftReference myCachedClass = new SoftReference(null);
  @Override
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


