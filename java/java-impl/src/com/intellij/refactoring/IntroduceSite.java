// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Where a {@link ModCommand}-based caller introduces a field or a variable when the expression to extract cannot be
 * found by its text range alone: the site knows how to open a writable copy of the file and how to find the expression
 * inside that copy.
 * <p>
 * A command built by an introduce refactoring may be re-entered several times, once per chooser round, each of which
 * starts from a fresh {@link ActionContext} on the physical file. A site must therefore be immutable and must not
 * capture PSI resolved against a copy of a previous round, except as a pure tree-position token passed to
 * {@link com.intellij.psi.util.PsiTreeUtil#findSameElementInCopy}.
 */
@ApiStatus.Internal
public interface IntroduceSite {
  /**
   * Finds the expression to extract inside the copy of the file being updated, and makes it usable by the refactoring
   * there: the copy is not physical, and the refactorings leave such elements alone unless they are marked with
   * {@link com.intellij.refactoring.introduceField.ElementToWorkOn#REPLACE_NON_PHYSICAL} or routed through
   * {@link com.intellij.refactoring.introduceField.ElementToWorkOn#getWritable}.
   *
   * @return the expression, or {@code null} if it is gone from the copy
   */
  @Nullable PsiExpression locate(@NotNull ModPsiUpdater updater);

  /**
   * Runs {@code action} on a writable copy of the file of {@code context}. The default opens a plain
   * {@link ModCommand#psiUpdate}; a caller that has to pre-process the copy, like a postfix template removing its
   * template key, overrides this.
   */
  default @NotNull ModCommand psiUpdate(@NotNull ActionContext context, @NotNull Consumer<@NotNull ModPsiUpdater> action) {
    return ModCommand.psiUpdate(context, action);
  }
}
