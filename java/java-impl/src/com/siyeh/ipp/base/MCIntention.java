// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.base;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A (mostly) drop-in ModCommandAction replacement for {@link Intention}
 */
public abstract class MCIntention implements ModCommandAction {
  private final Supplier<PsiElementPredicate> myPredicate = new SynchronizedClearableLazy<>(this::getElementPredicate);

  @Override
  public final @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PsiElement matchingElement = findMatchingElement(context);
    if (matchingElement == null) {
      return ModCommand.nop();
    }
    return ModCommand.psiUpdate(matchingElement, (e, updater) -> processIntention(e, context, updater));
  }

  protected void processIntention(@NotNull PsiElement element) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    processIntention(element);
  }

  @NotNull
  protected abstract PsiElementPredicate getElementPredicate();


  @Nullable
  PsiElement findMatchingElement(@Nullable PsiElement element, ActionContext context) {
    if (element == null || !JavaLanguage.INSTANCE.equals(element.getLanguage())) return null;

    PsiElementPredicate predicate = myPredicate.get();

    while (element != null) {
      if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
        break;
      }
      boolean satisfied = predicate instanceof PsiElementContextPredicate contextPredicate ?
                          contextPredicate.satisfiedBy(element, context) :
                          predicate.satisfiedBy(element);
      if (satisfied) {
        return element;
      }
      element = element.getParent();
      if (element instanceof PsiFile) {
        break;
      }
    }
    return null;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiElement element = findMatchingElement(context);
    if (element == null) return null;
    String text = getTextForElement(element);
    return Presentation.of(text == null ? getFamilyName() : text);
  }

  @Nullable
  protected PsiElement findMatchingElement(@NotNull ActionContext context) {
    PsiElement leaf = context.findLeaf();
    PsiElement element = findMatchingElement(leaf, context);
    if (element != null) return element;
    PsiElement leftLeaf = context.findLeafOnTheLeft();
    if (leftLeaf != leaf) return findMatchingElement(leftLeaf, context);
    return null;
  }

  protected abstract @IntentionName @Nullable String getTextForElement(@NotNull PsiElement element);
}