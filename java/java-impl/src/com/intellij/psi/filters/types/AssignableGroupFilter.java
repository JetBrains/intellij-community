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
package com.intellij.psi.filters.types;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.FalseFilter;
import com.intellij.psi.filters.InitializableFilter;
import com.intellij.psi.filters.OrFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 21:27:25
 * To change this template use Options | File Templates.
 */
public class AssignableGroupFilter extends OrFilter implements InitializableFilter{
  public AssignableGroupFilter(){}

  public AssignableGroupFilter(PsiClass[] classes){
    init(classes);
  }

  @Override
  public void init(Object[] classes){
    for (Object aClass : classes) {
      if (aClass instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass)aClass;
        PsiType type = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory().createType(psiClass, PsiSubstitutor.EMPTY);
        addFilter(new AssignableFromFilter(type));
      }
      if (aClass instanceof PsiType) {
        addFilter(new AssignableFromFilter((PsiType)aClass));
      }
    }
    addFilter(new FalseFilter());
  }
}