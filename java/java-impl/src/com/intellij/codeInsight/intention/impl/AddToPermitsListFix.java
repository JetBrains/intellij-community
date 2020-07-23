// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.intellij.util.ObjectUtils.tryCast;

public class AddToPermitsListFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final String myParentName;
  private final String myClassName;

  public AddToPermitsListFix(@NotNull PsiElement toHighlight, @NotNull PsiIdentifier classIdentifier) {
    super(toHighlight);
    myParentName = toHighlight.getText();
    myClassName = classIdentifier.getText();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(startElement, PsiClass.class);
    if (psiClass == null) return;
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) return;
    PsiJavaReference parentReference = tryCast(startElement, PsiJavaReference.class);
    if (parentReference == null) return;
    PsiClass parentClass = tryCast(parentReference.resolve(), PsiClass.class);
    if (parentClass == null) return;
    FillPermitsListFix.fillPermitsList(parentClass, Collections.singleton(qualifiedName));
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return JavaBundle.message("add.to.permits.list", myClassName, myParentName);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return getText();
  }
}
