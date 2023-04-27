// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public abstract class PsiBasedModCommandAction<E extends PsiElement> implements ModCommandAction {
  private final @NotNull SmartPsiElementPointer<E> myPointer;

  protected PsiBasedModCommandAction(@NotNull E element) {
    myPointer = SmartPointerManager.createPointer(element);
  }

  @Override
  public final boolean isAvailable(@NotNull ActionContext context) {
    E element = myPointer.getElement();
    return element != null && isAvailable(context, element);
  }

  @Override
  public final @NotNull ModCommand perform(@NotNull ActionContext context) {
    E element = myPointer.getElement();
    if (element == null) return ModNothing.NOTHING;
    return perform(context, element);
  }

  @Override
  public final @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    E element = myPointer.getElement();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    if (IntentionPreviewUtils.getOriginalFile(context.file()) == element.getContainingFile()) {
      element = PsiTreeUtil.findSameElementInCopy(element, context.file());
    }
    return generatePreview(context, element);
  }

  protected @NotNull IntentionPreviewInfo generatePreview(ActionContext context, E element) {
    ModCommand command = ModCommand.retrieve(() -> perform(context, element));
    return IntentionPreviewUtils.getModCommandPreview(command, context.file());
  }

  protected abstract @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull E element);

  protected boolean isAvailable(@NotNull ActionContext context, @NotNull E element) {
    return true;
  }
}
