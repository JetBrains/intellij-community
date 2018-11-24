/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.filters.FilterPositionUtil;
import org.jetbrains.annotations.Nullable;

public class AfterElement extends PositionElementFilter {

  public AfterElement(ElementFilter filter){
    setFilter(filter);
  }

  @Override
  public boolean isAcceptable(Object element, @Nullable PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    final PsiElement previous = FilterPositionUtil.searchNonSpaceNonCommentBack((PsiElement) element, true);
    if(previous != null){
      return getFilter().isAcceptable(previous, context);
    }
    return false;
  }

  public String toString(){
    return "after(" +getFilter()+")";
  }
}
