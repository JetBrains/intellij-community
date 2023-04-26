// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * A helper to perform editor command when building the {@link com.intellij.modcommand.ModCommand}
 * 
 * @see ModCommands#psiUpdate(PsiElement, BiConsumer) 
 */
public interface PsiUpdateContext {
  /**
   * Selects given element
   * 
   * @param element element to select
   */
  void select(@NotNull PsiElement element);

  /**
   * Navigates to a given element
   * 
   * @param element element to navigate to
   */
  void moveTo(@NotNull PsiElement element);
}
