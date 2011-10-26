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
 * Date: 11.02.2003
 * Time: 13:54:33
 * To change this template use Options | File Templates.
 */
public class SuperParentFilter extends PositionElementFilter{
  public SuperParentFilter(ElementFilter filter){
    setFilter(filter);
  }

  public SuperParentFilter(){}

  @Override
  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement)) return false;
    while((element = ((PsiElement) element).getParent()) != null){
      if(getFilter().isAcceptable(element, scope))
        return true;
    }
    return false;
  }


  public String toString(){
    return "super-parent(" +getFilter()+")";
  }
}
