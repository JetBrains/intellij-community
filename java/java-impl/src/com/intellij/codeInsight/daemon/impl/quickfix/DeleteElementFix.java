/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeleteElementFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myText;

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
    return ObjectUtils.notNull(myText, getFamilyName());
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

    public DeleteMultiFix(@NotNull PsiElement @NotNull ... elements) {
      myElements = elements;
    }

    @Override
    public @IntentionName @NotNull String getText() {
      return getFamilyName();
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