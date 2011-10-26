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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.FilterUtil;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:09:39
 * To change this template use Options | File Templates.
 */
public class StartElementFilter extends PositionElementFilter{
  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    return FilterUtil.getPreviousElement((PsiElement) element, false) == null;
  }

  public String toString(){
    return "start";
  }
}
