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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.04.2003
 * Time: 17:18:58
 * To change this template use Options | File Templates.
 */
public class FilterGetter implements ContextGetter{
  private final ContextGetter myBaseGetter;
  private final ElementFilter myFilter;

  public FilterGetter(ContextGetter baseGetter, ElementFilter filter){
    myBaseGetter = baseGetter;
    myFilter = filter;
  }

  @Override
  public Object[] get(PsiElement context, CompletionContext completionContext){
    final List results = new ArrayList();
    final Object[] elements = myBaseGetter.get(context, completionContext);
    for (final Object element : elements) {
      if (myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(element, context)) {
        results.add(element);
      }
    }
    return results.toArray();
  }
}
