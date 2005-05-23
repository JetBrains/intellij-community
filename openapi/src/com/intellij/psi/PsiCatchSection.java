package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface PsiCatchSection extends PsiElement {
  PsiCatchSection[] EMPTY_ARRAY = new PsiCatchSection[0];

  @Nullable
  PsiParameter getParameter();

  @Nullable
  PsiCodeBlock getCatchBlock();

  @Nullable
  PsiType getCatchType();

  @NotNull
  PsiTryStatement getTryStatement();
}
