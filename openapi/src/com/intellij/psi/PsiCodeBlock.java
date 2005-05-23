/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public interface PsiCodeBlock extends PsiElement{
  PsiCodeBlock[] EMPTY_ARRAY = new PsiCodeBlock[0];

  @NotNull
  PsiStatement[] getStatements();

  @Nullable
  PsiElement getFirstBodyElement();

  @Nullable
  PsiElement getLastBodyElement();

  @Nullable
  PsiJavaToken getLBrace();

  @Nullable
  PsiJavaToken getRBrace();

}
