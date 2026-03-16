/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.initialization;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SplitDeclarationAndInitializationIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("split.declaration.and.initialization.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("split.declaration.and.initialization.intention.name");
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new SplitDeclarationAndInitializationPredicate();
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiCodeBlock.class);
    if (codeBlock != null && PsiTreeUtil.isAncestor(element, codeBlock, true)) return null;
    return Presentation.of(getTextForElement(element));
  }

  @Override
  public void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiField field = (PsiField)element.getParent();
    final PsiExpression initializer = field.getInitializer();
    if (initializer == null) {
      return;
    }
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(initializer);
    if (surrounder == null) {
      return;
    }
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    updater.highlight(result.getAnchor());
  }
}