// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.WalkingState;
import org.jetbrains.annotations.NotNull;

public abstract class PsiWalkingState extends WalkingState<PsiElement> {
  private static final Logger LOG = Logger.getInstance(PsiWalkingState.class);
  private final PsiElementVisitor myVisitor;

  private static class PsiTreeGuide implements TreeGuide<PsiElement> {
    @Override
    public PsiElement getNextSibling(@NotNull PsiElement element) {
      return checkSanity(element, element.getNextSibling());
    }

    private static PsiElement checkSanity(PsiElement element, PsiElement sibling) {
      if (sibling == PsiUtilCore.NULL_PSI_ELEMENT) throw new PsiInvalidElementAccessException(element, "Sibling of "+element+" is NULL_PSI_ELEMENT");
      return sibling;
    }

    @Override
    public PsiElement getPrevSibling(@NotNull PsiElement element) {
      return checkSanity(element, element.getPrevSibling());
    }

    @Override
    public PsiElement getFirstChild(@NotNull PsiElement element) {
      return element.getFirstChild();
    }

    @Override
    public PsiElement getParent(@NotNull PsiElement element) {
      return element.getParent();
    }

    private static final PsiTreeGuide instance = new PsiTreeGuide();
  }

  protected PsiWalkingState(@NotNull PsiElementVisitor delegate) {
    this(delegate, PsiTreeGuide.instance);
  }
  protected PsiWalkingState(@NotNull PsiElementVisitor delegate, @NotNull TreeGuide<PsiElement> guide) {
    super(guide);
    myVisitor = delegate;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    element.accept(myVisitor);
  }

  @Override
  public void elementStarted(@NotNull PsiElement element) {
    if (!startedWalking && element instanceof PsiCompiledElement) {
      LOG.error(element + "; of class:" + element.getClass() + "; Do not use walking visitor inside compiled PSI since getNextSibling() is too slow there");
    }

    super.elementStarted(element);
  }
}
