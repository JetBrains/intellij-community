// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Allow conveniently implement surrounders (Surround With) when it was called on java expression.
 */
public abstract class JavaExpressionSurrounder implements Surrounder {
  public static final ExtensionPointName<JavaExpressionSurrounder> EP_NAME = ExtensionPointName.create("com.intellij.javaExpressionSurrounder");

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
  public TextRange surroundElements(@NotNull Project project,
                                    @NotNull Editor editor,
                                    PsiElement @NotNull [] elements) throws IncorrectOperationException {
    if (elements.length != 1 || !(elements[0] instanceof PsiExpression)) {
      throw new IllegalArgumentException(Arrays.toString(elements));
    }
    return surroundExpression(project, editor, (PsiExpression)elements[0]);
  }

  /**
   * Does the surrounding replacing some parent nodes.
   *
   * It is guaranteed that {@link JavaExpressionSurrounder#isApplicable)} is called and returned true before calling this method.
   *
   * @param expr expression on which the action was called
   */
  public abstract TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException;
}
