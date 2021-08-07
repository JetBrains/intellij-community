// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.interfacetoclass.ConvertInterfaceToClassIntention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertInterfaceToClassFix extends LocalQuickFixAndIntentionActionOnPsiElement implements PriorityAction {

  public ConvertInterfaceToClassFix(@Nullable PsiClass aClass) {
    super(aClass);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiClass)) return false;
    return ConvertInterfaceToClassIntention.canConvertToClass((PsiClass)startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiClass)) return;
    ConvertInterfaceToClassIntention.convert((PsiClass)startElement);
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.LOW;
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.name");
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.family.name");
  }
}
