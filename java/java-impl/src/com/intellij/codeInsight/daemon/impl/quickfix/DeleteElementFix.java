// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeleteElementFix extends PsiUpdateModCommandAction<PsiElement> {
  private final @Nls String myText;

  public DeleteElementFix(@NotNull PsiElement element) {
    super(element);
    myText = CommonQuickFixBundle.message("fix.remove.title", JavaElementKind.fromElement(element).object());
  }

  public DeleteElementFix(@NotNull PsiElement element, @NotNull @Nls String text) {
    super(element);
    myText = text;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return Presentation.of(myText == null ? getFamilyName() : myText).withFixAllOption(this);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.remove.title", JavaElementKind.UNKNOWN.object());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    new CommentTracker().deleteAndRestoreComments(element);
  }

  public static final class DeleteMultiFix implements ModCommandAction {
    private final @NotNull PsiElement @NotNull [] myElements;
    private @NotNull final @Nls String myMessage;

    public DeleteMultiFix(@NotNull PsiElement @NotNull ... elements) {
      myElements = elements;
      myMessage = getFamilyName();
    }

    public DeleteMultiFix(PsiElement @NotNull [] elements, @NotNull @Nls String message) {
      myElements = elements;
      myMessage = message;
    }

    @Override
    public @NotNull Presentation getPresentation(@NotNull ActionContext context) {
      return Presentation.of(myMessage);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.remove.title", JavaElementKind.UNKNOWN.object());
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      return ModCommand.psiUpdate(context, updater -> {
        for (PsiElement element : ContainerUtil.map(myElements, updater::getWritable)) {
          new CommentTracker().deleteAndRestoreComments(element);
        }
      });
    }
  }
}