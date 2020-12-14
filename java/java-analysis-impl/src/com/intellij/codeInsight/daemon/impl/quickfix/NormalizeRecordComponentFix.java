// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NormalizeRecordComponentFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public NormalizeRecordComponentFix(PsiRecordComponent component) {
    super(component);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    JavaSharedImplUtil.normalizeBrackets((PsiRecordComponent)startElement);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("c.style.array.declaration.replace.quickfix");
  }
}
