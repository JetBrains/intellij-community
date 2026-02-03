// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.base;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class MCIntention extends PsiUpdateModCommandAction<PsiElement> {
  private final Supplier<PsiElementPredicate> myPredicate = new SynchronizedClearableLazy<>(this::getElementPredicate);

  protected MCIntention() {
    super(PsiElement.class);
  }

  protected void invoke(@NotNull PsiElement element) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    invoke(element);
  }

  protected abstract @NotNull PsiElementPredicate getElementPredicate();

  @Override
  protected boolean isElementApplicable(@NotNull PsiElement element, @NotNull ActionContext context) {
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) return false;

    PsiElementPredicate predicate = myPredicate.get();
    return predicate instanceof PsiElementContextPredicate contextPredicate ?
           contextPredicate.satisfiedBy(element, context) :
           predicate.satisfiedBy(element);
  }

  @Override
  protected boolean stopSearchAt(@NotNull PsiElement element, @NotNull ActionContext context) {
    return !JavaLanguage.INSTANCE.equals(element.getLanguage());
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    String text = getTextForElement(element);
    return Presentation.of(text == null ? getFamilyName() : text);
  }

  protected abstract @IntentionName @Nullable String getTextForElement(@NotNull PsiElement element);
}