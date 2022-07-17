// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveLeftRight;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Instances of this class implement language-specific logic of <em>Code | Move Element Left/Right</em> actions.
 *
 * @see com.intellij.psi.PsiListLikeElement
 */
public abstract class MoveElementLeftRightHandler {
  public static final LanguageExtension<MoveElementLeftRightHandler> EXTENSION =
    new LanguageExtension<>("com.intellij.moveLeftRightHandler");

  /**
   * Returns sub-elements (usually children) of given PSI element, which can be moved using <em>Code | Move Element Left/Right</em> actions.
   * Should return an empty array if there are no such elements.
   */
  public abstract PsiElement @NotNull [] getMovableSubElements(@NotNull PsiElement element);
}
