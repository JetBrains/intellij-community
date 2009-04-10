/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class GenerationInfo {
  public static final GenerationInfo[] EMPTY_ARRAY = new GenerationInfo[0];
  
  public abstract void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException;

  public abstract PsiMember getPsiMember();

  /**
   * @param aClass
   * @param leaf leaf element. Is guaranteed to be a tree descendant of aClass.
   * @return the value that will be passed to the {@link #insert(com.intellij.psi.PsiClass, com.intellij.psi.PsiElement, boolean)} method later.
   */
  @Nullable
  public PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf) {
    PsiElement element = leaf;
    while (element.getParent() != aClass) {
      element = element.getParent();
    }

    PsiJavaToken lBrace = aClass.getLBrace();
    if (lBrace == null) {
      return null;
    }
    PsiJavaToken rBrace = aClass.getRBrace();
    if (!GenerateMembersUtil.isChildInRange(element, lBrace.getNextSibling(), rBrace)) {
      return null;
    }
    PsiElement prev = leaf.getPrevSibling();
    if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      element = leaf.getNextSibling();
    }
    return element;
  }
}
