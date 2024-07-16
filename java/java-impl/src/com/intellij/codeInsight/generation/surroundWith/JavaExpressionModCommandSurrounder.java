// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.ModCommandSurrounder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Allows implementing conveniently surrounders (Surround With) when it was called on java expression.
 */
public abstract class JavaExpressionModCommandSurrounder extends ModCommandSurrounder {
  public static final ExtensionPointName<JavaExpressionModCommandSurrounder> EP_NAME = ExtensionPointName.create("com.intellij.javaExpressionSurrounder");

  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    return elements.length == 1 &&
           elements[0] instanceof PsiExpression expr &&
           DumbService.getInstance(expr.getProject()).computeWithAlternativeResolveEnabled(() -> isApplicable(expr));
  }

  /**
   * @return true iff the expression can be surrounded using this instance.
   */
  public abstract boolean isApplicable(PsiExpression expr);

  @Override
  public final @NotNull ModCommand surroundElements(@NotNull ActionContext context, @NotNull PsiElement @NotNull [] elements) {
    if (elements.length != 1 || !(elements[0] instanceof PsiExpression expr)) {
      throw new IllegalArgumentException(Arrays.toString(elements));
    }
    return ModCommand.psiUpdate(context, updater -> surroundExpression(context, ElementToWorkOn.getWritable(expr, updater), updater));
  }

  /**
   * Performs the surrounding on non-physical copy, replacing some parent nodes.
   * <p>
   * It is guaranteed that {@link JavaExpressionModCommandSurrounder#isApplicable)} is called and returned true before calling this method.
   *
   * @param context action context
   * @param expr    expression on which the action was called
   * @param updater updater to use if necessary
   */
  protected abstract void surroundExpression(@NotNull ActionContext context, @NotNull PsiExpression expr, @NotNull ModPsiUpdater updater);
}
