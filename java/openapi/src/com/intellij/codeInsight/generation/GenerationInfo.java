// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Medvedev Max
 */
public interface GenerationInfo {
  GenerationInfo[] EMPTY_ARRAY = new GenerationInfo[0];

  void insert(@NotNull PsiClass aClass, @Nullable PsiElement anchor, boolean before) throws IncorrectOperationException;

  /**
   * @return the associated PSI member or null if it has become invalid
   */
  @Nullable
  PsiMember getPsiMember();

  /**
   * @param leaf leaf element. Is guaranteed to be a tree descendant of aClass.
   * @return the value that will be passed to the {@link #insert(PsiClass, PsiElement, boolean)} method later.
   */
  @Nullable
  PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf);

  /**
   * Position caret in generated element in correct way
   */
  void positionCaret(@NotNull Editor editor, boolean toEditMethodBody);

  /**
   * Position caret in generated element in correct way
   */
  void positionCaret(@NotNull ModPsiUpdater editor, boolean toEditMethodBody);
}