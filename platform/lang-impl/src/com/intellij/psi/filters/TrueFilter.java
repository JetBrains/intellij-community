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

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.02.2003
 * Time: 17:31:05
 * To change this template use Options | File Templates.
 */
public class TrueFilter implements ElementFilter{

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    return true;
  }

  public String toString(){
    return "true";
  }

  public static final ElementFilter INSTANCE = new TrueFilter();

  private TrueFilter() {}
}
