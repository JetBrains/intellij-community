// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class AddMethodBodyFix implements IntentionActionWithFixAllOption {
  private final PsiMethod myMethod;
  private final @Nls String myText;

  public AddMethodBodyFix(@NotNull PsiMethod method) {
    myMethod = method;
    myText = QuickFixBundle.message("add.method.body.text");
  }

  public AddMethodBodyFix(@NotNull PsiMethod method, @NotNull @Nls String text) {
    myMethod = method;
    myText = text;
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
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() &&
           myMethod.getBody() == null &&
           myMethod.getContainingClass() != null &&
           BaseIntentionAction.canModify(myMethod);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiUtil.setModifierProperty(myMethod, PsiModifier.ABSTRACT, false);
    if (Objects.requireNonNull(myMethod.getContainingClass()).isInterface() &&
        !myMethod.hasModifierProperty(PsiModifier.STATIC) &&
        !myMethod.hasModifierProperty(PsiModifier.DEFAULT) &&
        !myMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiUtil.setModifierProperty(myMethod, PsiModifier.DEFAULT, true);
    }
    CreateFromUsageUtils.setupMethodBody(myMethod);
    if (myMethod.getContainingFile() == file) {
      CreateFromUsageUtils.setupEditor(myMethod, editor);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return myMethod.getContainingFile();
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new AddMethodBodyFix(PsiTreeUtil.findSameElementInCopy(myMethod, target));
  }
}
