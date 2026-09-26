/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.generation.surroundWith.JavaWithCastSurrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class CastExpressionPostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  public CastExpressionPostfixTemplate() {
    super("cast", "((SomeType) expr)", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }


  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    PostfixTemplatesUtils.surround(new JavaWithCastSurrounder(), editor, expression);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull PostfixModExpander createModExpander() {
    return createModExpander((ctx, updater, elementInCopy) -> {
      JavaWithCastSurrounder surrounder = new JavaWithCastSurrounder();
      PsiElement[] elements = {elementInCopy};
      if (surrounder.isApplicable(elements) && elementInCopy instanceof PsiExpression expression) {
        surrounder.surroundExpression(ctx, expression, updater);
      }
    });
  }
}