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
package com.intellij.psi.filters.element;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 14:37:06
 * To change this template use Options | File Templates.
 */
public class PackageEqualsFilter
  implements ElementFilter{

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiClass.class, hintClass) || ReflectionCache.isAssignable(PsiPackage.class, hintClass);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    final String elementPackName = getPackageName((PsiElement) element);
    final String contextPackName = getPackageName(context);
    return elementPackName != null && contextPackName != null && elementPackName.equals(contextPackName);
  }

  protected static String getPackageName(PsiElement element){
    if(element instanceof PsiPackage){
      return ((PsiPackage)element).getQualifiedName();
    }
    if(element.getContainingFile() instanceof PsiJavaFile){
      return ((PsiJavaFile)element.getContainingFile()).getPackageName();
    }
    return null;
  }


  public String toString(){
    return "same-package";
  }
}
