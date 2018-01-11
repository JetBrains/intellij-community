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
package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.impl.CollapseBlockHandler;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class CollapseBlockAction  extends BaseCodeInsightAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new CollapseBlockHandler() {
      protected PsiElement findParentBlock(@Nullable PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
      }

      protected boolean isRBrace(@Nullable PsiElement element) {
        return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE;
      }
    };
  }
}
