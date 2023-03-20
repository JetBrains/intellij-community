// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NavigateToDuplicateElementFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final @IntentionName String myText;

  public NavigateToDuplicateElementFix(@NotNull NavigatablePsiElement element) {
    super(element);
    myText = QuickFixBundle.message("navigate.duplicate.element.text", JavaElementKind.fromElement(element).object());
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    NavigatablePsiElement navigatablePsiElement = ObjectUtils.tryCast(startElement, NavigatablePsiElement.class);
    return navigatablePsiElement != null &&
           navigatablePsiElement.isValid() &&
           BaseIntentionAction.canModify(navigatablePsiElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!(startElement instanceof NavigatablePsiElement)) return;
    ((NavigatablePsiElement)startElement).navigate(true);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    NavigatablePsiElement element = ObjectUtils.tryCast(getStartElement(), NavigatablePsiElement.class);
    if (element == null) return IntentionPreviewInfo.EMPTY;
    return IntentionPreviewInfo.navigate(element);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
