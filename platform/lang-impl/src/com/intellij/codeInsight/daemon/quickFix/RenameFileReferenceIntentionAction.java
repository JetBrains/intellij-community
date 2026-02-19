// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RenameFileReferenceIntentionAction implements IntentionAction, LocalQuickFix {
  private final String myExistingElementName;
  private final FileReference myFileReference;

  RenameFileReferenceIntentionAction(final String existingElementName, final FileReference fileReference) {
    myExistingElementName = existingElementName;
    myFileReference = fileReference;
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message("rename.file.reference.text", myExistingElementName);
  }

  @Override
  public @NotNull String getName() {
    return getText();
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("rename.file.reference.family");
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    if (isAvailable(project, null, null)) {
      invoke(project, null, descriptor.getPsiElement().getContainingFile());
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiElement element = myFileReference.getElement();
    PsiElement copy = PsiTreeUtil.findSameElementInCopy(element, target);
    return StreamEx.of(copy.getReferences()).select(FileReference.class).collect(MoreCollectors.onlyOne())
      .map(ref -> new RenameFileReferenceIntentionAction(myExistingElementName, ref)).orElse(null);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    myFileReference.handleElementRename(myExistingElementName);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
