/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaRainbowVisitor extends RainbowVisitor {
  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return file instanceof PsiJavaFile;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression
        || element instanceof PsiLocalVariable
        || element instanceof PsiParameter
        || element instanceof PsiDocParamRef) {
      PsiElement context = PsiTreeUtil.findFirstParent(element, p -> p instanceof PsiMethod);
      if (context != null) {
        HighlightInfo attrs = getRainbowSymbolKey(
          context,
          element instanceof PsiReferenceExpression
          ? Pair.create(element, ((PsiReferenceExpression)element).resolve())
          : element instanceof PsiDocParamRef
            ? Pair.create(element, element.getReference() == null ? null : element.getReference().resolve())
            : Pair.create(((PsiVariable)element).getNameIdentifier(), element));
        addInfo(attrs);
      }
    }
  }

  @Nullable
  private HighlightInfo getRainbowSymbolKey(@NotNull PsiElement context, @NotNull Pair<PsiElement, PsiElement> rainbow) {
    if (rainbow.first == null || rainbow.second == null) {
      return null;
    }
    if (rainbow.second instanceof PsiLocalVariable || rainbow.second instanceof PsiParameter) {
      String name = ((PsiVariable)rainbow.second).getName();
      if (name != null) {
        return getInfo(context, rainbow.first, name, rainbow.second instanceof PsiLocalVariable
                                                     ? JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES
                                                     : rainbow.first instanceof PsiDocTagValue
                                                       ? JavaHighlightingColors.DOC_COMMENT_TAG_VALUE
                                                       : JavaHighlightingColors.PARAMETER_ATTRIBUTES);
      }
    }
    return null;
  }

  @Override
  @NotNull
  public HighlightVisitor clone() {
    return new JavaRainbowVisitor();
  }
}

