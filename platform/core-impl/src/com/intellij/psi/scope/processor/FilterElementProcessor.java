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

package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.PsiElementProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 14:33:56
 * To change this template use Options | File Templates.
 */
public class FilterElementProcessor implements PsiElementProcessor{
  private final List<PsiElement> myResults;
  private final ElementFilter myFilter;
  private final PsiElementProcessor myProcessor;

  public FilterElementProcessor(ElementFilter filter,  PsiElementProcessor processor, List container){
    myFilter = filter;
    myProcessor = processor;
    myResults = container;
  }


  public FilterElementProcessor(ElementFilter filter, List container){
    this(filter,  null, container);
  }

  public FilterElementProcessor(ElementFilter filter, PsiElementProcessor proc){
    this(filter, proc, new ArrayList());
  }


  public FilterElementProcessor(ElementFilter filter){
    this(filter, null, new ArrayList());
  }

  @Override
  public boolean execute(@NotNull PsiElement element){
    if(myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(element, element.getParent())){
      if(myProcessor != null){
        return myProcessor.execute(element);
      }
      add(element);
    }
    return true;
  }

  protected void add(PsiElement element){
    myResults.add(element);
  }

  public List<PsiElement> getResults(){
    return myResults;
  }

  public boolean shouldProcess(Class elementClass){
    return myFilter.isClassAcceptable(elementClass);
  }
}
