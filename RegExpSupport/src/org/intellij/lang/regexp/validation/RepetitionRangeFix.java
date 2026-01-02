// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.validation;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.inspection.RegExpReplacementUtil;
import org.intellij.lang.regexp.psi.RegExpNumber;
import org.intellij.lang.regexp.psi.RegExpQuantifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
final class RepetitionRangeFix extends PsiUpdateModCommandAction<RegExpQuantifier> {

  RepetitionRangeFix(@NotNull RegExpQuantifier quantifier) {
    super(quantifier);
  }

  @Override
  public @NotNull String getFamilyName() {
    return RegExpBundle.message("quickfix.family.name.flip.bounds");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull RegExpQuantifier quantifier) {
    final RegExpNumber min = quantifier.getMin();
    final RegExpNumber max = quantifier.getMax();
    if (min == null || max == null) return null;
    return Presentation.of(RegExpBundle.message("intention.name.flip.elements", min.getText(), max.getText()));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull RegExpQuantifier quantifier, @NotNull ModPsiUpdater updater) {
    RegExpReplacementUtil.flipLeftRight(quantifier.getMin(), quantifier.getMax());
  }
}
