// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface needs to be implemented by implementers of {@link ProblemGroup}
 * that support suppressing of problems.
 */
public interface SuppressableProblemGroup extends ProblemGroup {
  /**
   * Returns the list of suppression actions for the specified element.
   *
   * @param element the element on which Alt-Enter is pressed, or null if getting the list of available suppression actions in
   *                Inspections tool window
   * @return the array of suppression actions.
   */
  SuppressIntentionAction @NotNull [] getSuppressActions(final @Nullable PsiElement element);
}
