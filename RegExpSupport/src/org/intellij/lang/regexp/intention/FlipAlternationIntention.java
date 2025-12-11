// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.intention;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
final class FlipAlternationIntention extends PsiUpdateModCommandAction<PsiElement> {

  FlipAlternationIntention() {
    super(PsiElement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return RegExpBundle.message("intention.family.name.flip.alternation");
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return Presentation.of(RegExpBundle.message("intention.name.flip.alternation"));
  }

  @Override
  protected boolean isElementApplicable(@NotNull PsiElement element, @NotNull ActionContext context) {
    return element instanceof LeafPsiElement leaf 
           && leaf.getElementType() == RegExpTT.UNION 
           && element.getParent() instanceof RegExpPattern;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiElement left = element.getPrevSibling();
    final PsiElement right = element.getNextSibling();
    if (left == null || right == null) return;
    final PsiElement copyLeft = left.copy();
    final PsiElement copyRight = right.copy();
    left.replace(copyRight);
    right.replace(copyLeft);
  }
}