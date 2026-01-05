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
import org.intellij.lang.regexp.inspection.RegExpReplacementUtil;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpClass;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
final class FlipElementsIntention extends PsiUpdateModCommandAction<PsiElement> {

  FlipElementsIntention() {
    super(PsiElement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return RegExpBundle.message("intention.family.name.flip.elements");
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    final String left = element instanceof RegExpChar ? element.getText() : element.getPrevSibling().getText();
    final String right = element.getNextSibling().getText();
    return Presentation.of(RegExpBundle.message("intention.name.flip.elements", left, right));
  }

  @Override
  protected boolean isElementApplicable(@NotNull PsiElement el, @NotNull ActionContext context) {
    return switch (el) {
      case LeafPsiElement leaf when leaf.getElementType() == RegExpTT.UNION && el.getParent() instanceof RegExpPattern -> true;
      case RegExpChar ignored when el.getNextSibling() instanceof RegExpChar && el.getParent() instanceof RegExpClass -> true; 
      default -> false;
    };
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    RegExpReplacementUtil.flipLeftRight((element instanceof RegExpChar) ? element : element.getPrevSibling(), element.getNextSibling());
  }
}