// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class SealClassFromPermitsListAction extends LocalQuickFixAndIntentionActionOnPsiElement {

  public SealClassFromPermitsListAction(@Nullable PsiElement element) {
    super(element);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    PsiClass psiClass = ObjectUtils.tryCast(getStartElement(), PsiClass.class);
    return psiClass != null && DirectClassInheritorsSearch.search(psiClass).findFirst() != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass psiClass = ObjectUtils.tryCast(startElement, PsiClass.class);
    if (psiClass == null || psiClass.hasModifierProperty(PsiModifier.SEALED)) return;
    SealClassAction.sealClass(project, editor, psiClass);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return QuickFixBundle.message("seal.class.from.permits.list.fix");
  }
}
