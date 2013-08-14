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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:37:26
 * To change this template use Options | File Templates.
 */
public class AnyInnerFilter implements ElementFilter{
  private final ElementFilter myFilter;
  public AnyInnerFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  @Override
  public boolean isAcceptable(Object classElement, PsiElement place){
    if(classElement instanceof PsiClass){
      Project project = ((PsiClass)classElement).getProject();
      final PsiClass[] inners = ((PsiClass)classElement).getInnerClasses();
      for (final PsiClass inner : inners) {
        if (inner.hasModifierProperty(PsiModifier.STATIC)
            && PsiUtil.isAccessible(project, inner, place, null)
            && myFilter.isAcceptable(inner, place)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  public String toString(){
    return "any-inner(" + getFilter().toString() + ")";
  }
}
