// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.validation;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.inspection.RegExpReplacementUtil;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpCharRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
final class CharRangeFix extends PsiUpdateModCommandAction<RegExpCharRange> {

  CharRangeFix(@NotNull RegExpCharRange charRange) {
    super(charRange);
  }

  @Override
  public @NotNull String getFamilyName() {
    return RegExpBundle.message("quickfix.family.name.flip.bounds");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull RegExpCharRange charRange) {
    final RegExpChar from = charRange.getFrom();
    final RegExpChar to = charRange.getTo();
    if (to == null) return null;
    return Presentation.of(RegExpBundle.message("intention.name.flip.elements", from.getText(), to.getText()));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull RegExpCharRange charRange, @NotNull ModPsiUpdater updater) {
    RegExpReplacementUtil.flipLeftRight(charRange.getFrom(), charRange.getTo());
  }
}
