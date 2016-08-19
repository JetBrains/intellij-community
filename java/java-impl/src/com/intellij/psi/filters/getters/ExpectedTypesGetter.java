/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 09.04.2003
 * Time: 12:37:26
 * To change this template use Options | File Templates.
 */
public class ExpectedTypesGetter implements ContextGetter{

  @Override
  @NotNull
  public PsiType[] get(PsiElement context, CompletionContext completionContext){
    return getExpectedTypes(context, false);
  }

  public static PsiType[] getExpectedTypes(final PsiElement context, boolean defaultTypes) {
    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    if(expression == null) return PsiType.EMPTY_ARRAY;

    return extractTypes(ExpectedTypesProvider.getExpectedTypes(expression, true), defaultTypes);
  }

  public static PsiType[] extractTypes(ExpectedTypeInfo[] infos, boolean defaultTypes) {
    Set<PsiType> result = new THashSet<>(infos.length);
    for (ExpectedTypeInfo info : infos) {
      final PsiType type = info.getType();
      final PsiType defaultType = info.getDefaultType();
      if (!defaultTypes && !defaultType.equals(type)) {
        result.add(type);
      }
      result.add(defaultType);
    }
    return result.toArray(PsiType.createArray(result.size()));
  }
}
