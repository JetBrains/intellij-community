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

package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:51:02
 * To change this template use Options | File Templates.
 */
public abstract class PositionElementFilter implements ElementFilter {
  private ElementFilter myFilter;

  public void setFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  protected static PsiElement getOwnerChild(final PsiElement scope, PsiElement element){
    while(element != null && element.getParent() != scope){
      element = element.getParent();
    }
    return element;
  }
}
