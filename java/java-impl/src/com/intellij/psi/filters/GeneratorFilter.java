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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 19:55:15
 * To change this template use Options | File Templates.
 */
public class GeneratorFilter implements ElementFilter{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.filters.GeneratorFilter");
  private final ContextGetter myGetter;
  private final Class myFilterClass;

  public GeneratorFilter(Class filterClass, ContextGetter getter){
    myFilterClass = filterClass;
    myGetter = getter;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    final ElementFilter filter = getFilter();
    return filter == null || filter.isClassAcceptable(hintClass);
  }


  private SoftReference<PsiElement> myCachedElement = new SoftReference<PsiElement>(null);
  private SoftReference<ElementFilter> myCachedFilter = new SoftReference<ElementFilter>(null);

  private ElementFilter getFilter(){
    return myCachedFilter.get();
  }

  private ElementFilter getFilter(PsiElement context){
    ElementFilter filter = myCachedFilter.get();
    if(myCachedElement.get() != context || filter == null){
      filter = generateFilter(context);
      myCachedFilter = new SoftReference<ElementFilter>(filter);
      myCachedElement = new SoftReference<PsiElement>(context);
    }
    return filter;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element == null) return false;
    final ElementFilter filter = getFilter(context);
    return filter != null && filter.isAcceptable(element, context);
  }

  private ElementFilter generateFilter(PsiElement context){
    try{
      final ElementFilter elementFilter = (ElementFilter) myFilterClass.newInstance();
      final Object[] initArgument = myGetter.get(context, null);
      if(ReflectionCache.isAssignable(InitializableFilter.class, myFilterClass) && initArgument != null){
        ((InitializableFilter)elementFilter).init(initArgument);
        return elementFilter;
      }
      else{
        LOG.error("Filter initialization failed!");
      }
    }
    catch(InstantiationException e){
      LOG.error(e);
    }
    catch(IllegalAccessException e){
      LOG.error(e);
    }
    return null;
  }

}
