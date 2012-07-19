/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A filter to temporarily suppress highlighting errors inside lambda expressions.
 * todo[r.sh] stub; remove after implementing type inference
 */
public class JavaHighlightInfoFilter implements HighlightInfoFilter {
  @Override
  public boolean accept(@NotNull final HighlightInfo info, @Nullable final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return true;
    if (info.getSeverity() != HighlightSeverity.ERROR) return true;
    if (description(info, "Lambda expressions are not supported at this language level")) return true;

    final PsiElement element = file.findElementAt(info.getStartOffset());
    if (element == null) return true;
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class, false);
    if (lambdaExpression != null) {
      if (lambdaExpression.getParent() instanceof PsiExpressionList) return false;
      if (isLambdaInAmbiguousCall(info, element)) return false;
    }
    return true;
  }

  private static boolean isLambdaInAmbiguousCall(final HighlightInfo info, final PsiElement element) {
    if (!description(info, "Ambiguous method call")) return false;
    final PsiElement exprList = element.getParent();
    if (!(exprList instanceof PsiExpressionList)) return false;
    return PsiTreeUtil.getChildOfType(exprList, PsiLambdaExpression.class) != null;
  }

  private static boolean description(final HighlightInfo info, final String prefix) {
    return info.description != null && info.description.startsWith(prefix);
  }
}
