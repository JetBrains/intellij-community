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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 21:22:31
 * To change this template use Options | File Templates.
 */
public class ConstructorFilter extends ClassFilter {
  public ConstructorFilter(){
    super(PsiMethod.class);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiMethod){
      return ((PsiMethod)element).isConstructor();
    }
    return false;
  }
  public String toString(){
    return "constructor";
  }
}
