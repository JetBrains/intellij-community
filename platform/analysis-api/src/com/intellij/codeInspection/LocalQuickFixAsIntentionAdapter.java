// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LocalQuickFixAsIntentionAdapter implements IntentionAction, CustomizableIntentionAction {
  private final LocalQuickFix myFix;
  private final @NotNull ProblemDescriptor myProblemDescriptor;

  public LocalQuickFixAsIntentionAdapter(@NotNull LocalQuickFix fix, @NotNull ProblemDescriptor problemDescriptor) {
    myFix = fix;
    myProblemDescriptor = problemDescriptor;
  }

  @Override
  public @NotNull String getText() {
    return myFix.getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myFix.getFamilyName();
  }

  @ApiStatus.Internal
  @NotNull
  public LocalQuickFix getFix() {
    return myFix;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myProblemDescriptor.getStartElement() != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myFix.applyFix(project, myProblemDescriptor);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return myFix.getElementToMakeWritable(currentFile);
  }

  @Override
  public boolean startInWriteAction() {
    return myFix.startInWriteAction();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
    return myFix.generatePreview(project, myProblemDescriptor.getDescriptorForPreview(file));
  }

  @Override
  public boolean isShowSubmenu() {
    return myFix instanceof CustomizableIntentionAction ? ((CustomizableIntentionAction)myFix).isShowSubmenu()
                                                        : CustomizableIntentionAction.super.isShowSubmenu();
  }

  @Override
  public boolean isSelectable() {
    return myFix instanceof CustomizableIntentionAction ? ((CustomizableIntentionAction)myFix).isSelectable()
                                                        : CustomizableIntentionAction.super.isSelectable();
  }

  @Override
  public boolean isShowIcon() {
    return myFix instanceof CustomizableIntentionAction ? ((CustomizableIntentionAction)myFix).isShowIcon()
                                                        : CustomizableIntentionAction.super.isShowIcon();
  }

  @Override
  public String getTooltipText() {
    return myFix instanceof CustomizableIntentionAction ? ((CustomizableIntentionAction)myFix).getTooltipText()
                                                        : CustomizableIntentionAction.super.getTooltipText();
  }

  @Override
  public @NotNull List<RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    return myFix instanceof CustomizableIntentionAction ? ((CustomizableIntentionAction)myFix).getRangesToHighlight(editor, file)
                                                        : CustomizableIntentionAction.super.getRangesToHighlight(editor, file);
  }
}

