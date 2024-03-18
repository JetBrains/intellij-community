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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaRainbowVisitor extends RainbowVisitor {
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
      PsiElement context = PsiTreeUtil.findFirstParent(element, p -> p instanceof PsiMethod || p instanceof PsiClassInitializer || p instanceof PsiLambdaExpression);
      if (context != null) {
        PsiElement rainbowElement = element instanceof PsiReferenceExpression || element instanceof PsiDocParamRef
                            ? element : ((PsiVariable)element).getNameIdentifier();
        PsiElement resolved = element instanceof PsiReferenceExpression
                             ? ((PsiReferenceExpression)element).resolve()
                             : element instanceof PsiDocParamRef
                               ? element.getReference() == null ? null : element.getReference().resolve()
                               : element;
        HighlightInfo attrs = getRainbowSymbolKey(context, rainbowElement, resolved);
        addInfo(attrs);
      }
    }
  }

  @Nullable
  private HighlightInfo getRainbowSymbolKey(@NotNull PsiElement context, PsiElement rainbowElement, PsiElement resolved) {
    if (rainbowElement == null || resolved == null) {
      return null;
    }
    if (PsiUtil.isJvmLocalVariable(resolved)) {
      String name = ((PsiVariable)resolved).getName();
      if (name != null) {
        return getInfo(context, rainbowElement, name, resolved instanceof PsiLocalVariable
                                                     ? JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES
                                                     : rainbowElement instanceof PsiDocTagValue
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

