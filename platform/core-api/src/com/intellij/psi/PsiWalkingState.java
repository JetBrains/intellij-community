// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.PsiVersioningService;
import com.intellij.util.Processor;
import com.intellij.util.WalkingState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsiWalkingState extends WalkingState<PsiElement> {
  private static final Logger LOG = Logger.getInstance(PsiWalkingState.class);
  private final PsiElementVisitor myVisitor;


  public static boolean processAll(@NotNull PsiElement root, final @NotNull Processor<? super PsiElement> processor) {
    return processAll(root, new PsiWalkingState.PsiTreeGuide(), processor);
  }

  private static class PsiTreeGuide implements TreeGuide<PsiElement> {
    private final @Nullable PsiVersioningService service = ApplicationManager.getApplication().getService(PsiVersioningService.class);
    private final long version = service == null ? -1 : service.getCurrentVersion();

    @Override
    public PsiElement getNextSibling(@NotNull PsiElement element) {
      PsiElement nextSibling = service == null ? element.getNextSibling() : service.getNextSibling(element, version);
      return checkSanity(element, nextSibling);
    }

    private static PsiElement checkSanity(PsiElement element, PsiElement sibling) {
      if (sibling == PsiUtilCore.NULL_PSI_ELEMENT) throw new PsiInvalidElementAccessException(element, "Sibling of "+element+" is NULL_PSI_ELEMENT");
      return sibling;
    }

    @Override
    public PsiElement getPrevSibling(@NotNull PsiElement element) {
      PsiElement prevSibling = service == null ? element.getPrevSibling() : service.getPrevSibling(element, version);
      return checkSanity(element, prevSibling);
    }

    @Override
    public PsiElement getFirstChild(@NotNull PsiElement element) {
      return service == null ? element.getFirstChild() : service.getFirstChild(element, version);
    }

    @Override
    public PsiElement getParent(@NotNull PsiElement element) {
      return service == null ? element.getParent() : service.getParent(element, version);
    }
  }

  protected PsiWalkingState(@NotNull PsiElementVisitor delegate) {
    this(delegate, new PsiTreeGuide());
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
