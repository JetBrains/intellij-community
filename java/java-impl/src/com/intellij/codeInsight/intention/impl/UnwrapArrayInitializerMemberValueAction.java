// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnwrapArrayInitializerMemberValueAction extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myInitializerText;

  public UnwrapArrayInitializerMemberValueAction(@NotNull PsiArrayInitializerMemberValue arrayValue) {
    super(arrayValue);
    final PsiAnnotationMemberValue initializer = ArrayUtil.getFirstElement(arrayValue.getInitializers());
    myInitializerText = (initializer != null) ? initializer.getText() : null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiArrayInitializerMemberValue arrayValue = ObjectUtils.tryCast(startElement, PsiArrayInitializerMemberValue.class);
    if (arrayValue == null) return false;
    return arrayValue.getInitializers().length == 1;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiArrayInitializerMemberValue arrayValue = ObjectUtils.tryCast(startElement, PsiArrayInitializerMemberValue.class);
    if (arrayValue == null) return;
    final CommentTracker ct = new CommentTracker();
    ct.replaceAndRestoreComments(arrayValue, arrayValue.getInitializers()[0]);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return CommonQuickFixBundle.message("fix.unwrap", myInitializerText);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return QuickFixBundle.message("unwrap.array.initializer.member.value.fix");
  }
}
