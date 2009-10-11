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

import com.intellij.psi.*;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.12.2003
 * Time: 17:39:34
 * To change this template use Options | File Templates.
 */
public class InstanceOfLeftPartTypeGetter {
  public static PsiType[] getLeftTypes(PsiElement context) {
    if((context = FilterUtil.getPreviousElement(context, true)) == null) return PsiType.EMPTY_ARRAY;
    if(!PsiKeyword.INSTANCEOF.equals(context.getText())) return PsiType.EMPTY_ARRAY;
    if((context = FilterUtil.getPreviousElement(context, false)) == null) return PsiType.EMPTY_ARRAY;

    final PsiExpression contextOfType = PsiTreeUtil.getContextOfType(context, PsiExpression.class, false);
    if (contextOfType == null) return PsiType.EMPTY_ARRAY;

    PsiType type = contextOfType.getType();
    if (type == null) return PsiType.EMPTY_ARRAY;

    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass instanceof PsiTypeParameter) {
        return psiClass.getExtendsListTypes();
      }
    }

    return new PsiType[]{type};
  }
}
