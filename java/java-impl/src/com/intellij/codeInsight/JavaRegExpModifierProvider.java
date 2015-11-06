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
package com.intellij.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpModifierProvider;

/**
 * @author Konstantin Bulenkov
 */
public class JavaRegExpModifierProvider implements RegExpModifierProvider {
  @Override
  public int getFlags(PsiElement elementInHost, PsiFile regexp) {
    final PsiExpressionList list = PsiTreeUtil.getParentOfType(elementInHost, PsiExpressionList.class);
    if (list != null && list.getExpressions().length == 2 && PsiType.INT.equals(list.getExpressionTypes()[1])) {
      final Object result = JavaConstantExpressionEvaluator.computeConstantExpression(list.getExpressions()[1], false);
      if (result instanceof Integer) {
        //noinspection MagicConstant
        return ((Integer)result).intValue();
      }
    }
    return 0;
  }
}
