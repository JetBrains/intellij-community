// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GenerationInfoBase implements GenerationInfo {

  @Override
  public abstract void insert(@NotNull PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException;

  @Override
  public abstract PsiMember getPsiMember();

  @Override
  @Nullable
  public PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf) {
    PsiElement element = leaf;
    while (element.getParent() != aClass) {
      element = element.getParent();
      if (element == null) return null;
    }
    if (element instanceof PsiErrorElement) {
      return null;
    }

    PsiElement lBrace = aClass.getLBrace();
    if (lBrace == null) {
      return null;
    }
    PsiElement rBrace = aClass.getRBrace();
    if (!GenerateMembersUtil.isChildInRange(element, lBrace.getNextSibling(), rBrace)) {
      return null;
    }
    if (leaf.getParent() == aClass && PsiUtilCore.getElementType(leaf.getPrevSibling()) == JavaTokenType.END_OF_LINE_COMMENT) {
      element = leaf.getNextSibling();
    }
    return element;
  }

  @Override
  public void positionCaret(@NotNull Editor editor, boolean toEditMethodBody) {
    PsiMember member = getPsiMember();
    if (member != null) {
      GenerateMembersUtil.positionCaret(editor, member, toEditMethodBody);
    }
  }

  @Override
  public void positionCaret(@NotNull ModPsiUpdater updater, boolean toEditMethodBody) {
    PsiMember member = getPsiMember();
    if (member != null) {
      GenerateMembersUtil.positionCaret(updater, member, toEditMethodBody);
    }
  }
}
