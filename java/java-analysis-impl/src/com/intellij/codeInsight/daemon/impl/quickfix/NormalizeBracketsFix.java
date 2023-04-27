// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NormalizeBracketsFix extends LocalQuickFixAndIntentionActionOnPsiElement
  implements IntentionActionWithFixAllOption {
  public NormalizeBracketsFix(PsiVariable variable) {
    super(variable);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    JavaSharedImplUtil.normalizeBrackets((PsiVariable)startElement);
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
