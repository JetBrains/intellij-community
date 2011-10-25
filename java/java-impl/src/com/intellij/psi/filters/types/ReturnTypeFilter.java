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

import com.intellij.codeInsight.template.Template;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.ReflectionCache;


/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:05:52
 * To change this template use Options | File Templates.
 */
public class ReturnTypeFilter implements ElementFilter{
  private final ElementFilter myFilter;


  public ReturnTypeFilter(ElementFilter filter){
    myFilter = filter;
  }
  @Override
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiVariable.class, hintClass) || ReflectionCache.isAssignable(PsiMethod.class, hintClass) ||
           ReflectionCache.isAssignable(PsiExpression.class, hintClass) || ReflectionCache.isAssignable(Template.class, hintClass) ||
           ReflectionCache.isAssignable(CandidateInfo.class, hintClass) || ReflectionCache.isAssignable(PsiKeyword.class, hintClass);

  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    return myFilter.isAcceptable(element, context);
  }

}
