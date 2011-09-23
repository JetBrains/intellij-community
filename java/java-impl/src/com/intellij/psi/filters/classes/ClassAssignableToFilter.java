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
package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 26.03.2003
 * Time: 21:02:40
 * To change this template use Options | File Templates.
 */
public class ClassAssignableToFilter extends ClassAssignableFilter{
  public ClassAssignableToFilter(String className){
    myClassName = className;
  }

  public ClassAssignableToFilter(PsiClass psiClass){
    myClass = psiClass;
  }

  public ClassAssignableToFilter(){}

  public boolean isAcceptable(Object aClass, PsiElement context){
    if(aClass instanceof PsiClass){
      PsiManager manager = ((PsiElement) aClass).getManager();
      final PsiClass psiClass = getPsiClass(manager, context.getResolveScope());
      return psiClass == aClass || ((PsiClass) aClass).isInheritor(psiClass, true);
    }
    return false;
  }

  public String toString(){
    return "class-assignable-to(" + getPsiClass(null, null) + ")";
  }
}
