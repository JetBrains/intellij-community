/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface GenerationInfo {
  GenerationInfo[] EMPTY_ARRAY = new GenerationInfo[0];
  
  void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException;

  @NotNull
  PsiMember getPsiMember();
}
