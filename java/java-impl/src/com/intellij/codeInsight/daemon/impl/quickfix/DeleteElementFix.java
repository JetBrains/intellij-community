// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeleteElementFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {
  private final @Nls String myText;

  public DeleteElementFix(@NotNull PsiElement element) {
    super(element);
    myText = CommonQuickFixBundle.message("fix.remove.title", JavaElementKind.fromElement(element).object());
  }

  public DeleteElementFix(@NotNull PsiElement element, @NotNull @Nls String text) {
    super(element);
    myText = text;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return myText == null ? getFamilyName() : myText;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.remove.title", JavaElementKind.UNKNOWN.object());
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    new CommentTracker().deleteAndRestoreComments(startElement);
  }

  public static final class DeleteMultiFix implements IntentionAction {
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
    public @IntentionName @NotNull String getText() {
      return myMessage;
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.remove.title", JavaElementKind.UNKNOWN.object());
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      for (PsiElement element : myElements) {
        new CommentTracker().deleteAndRestoreComments(element);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }

    @Override
    public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      return new DeleteMultiFix(ContainerUtil.map2Array(myElements, PsiElement.class, e -> PsiTreeUtil.findSameElementInCopy(e, target)));
    }
  }
}