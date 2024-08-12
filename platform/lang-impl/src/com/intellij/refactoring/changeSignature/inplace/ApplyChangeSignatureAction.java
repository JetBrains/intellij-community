// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature.inplace;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ApplyChangeSignatureAction extends BaseRefactoringIntentionAction {
  private final String myMethodName;

  public ApplyChangeSignatureAction(String methodName) {
    myMethodName = methodName;
  }

  @Override
  public @NotNull String getText() {
    return RefactoringBundle.message("changing.signature.of.0", myMethodName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return RefactoringBundle.message("intention.family.name.apply.signature.change");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final LanguageChangeSignatureDetector<ChangeInfo> detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());
    if (detector != null) {
      InplaceChangeSignature changeSignature = InplaceChangeSignature.getCurrentRefactoring(editor);
      ChangeInfo currentInfo = changeSignature != null ? changeSignature.getCurrentInfo() : null;
      if (currentInfo != null && detector.isChangeSignatureAvailableOnElement(element, currentInfo)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    InplaceChangeSignature signatureGestureDetector = InplaceChangeSignature.getCurrentRefactoring(editor);
    final String initialSignature = signatureGestureDetector.getInitialSignature();
    final ChangeInfo currentInfo = signatureGestureDetector.getCurrentInfo();
    signatureGestureDetector.detach();

    final LanguageChangeSignatureDetector<ChangeInfo> detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());

    detector.performChange(currentInfo, editor, initialSignature);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return file;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
