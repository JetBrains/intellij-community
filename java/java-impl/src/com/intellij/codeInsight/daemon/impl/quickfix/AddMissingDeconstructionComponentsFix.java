// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AddMissingDeconstructionComponentsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final @NotNull Collection<QuickFixFactory.Pattern> myMissingPatterns;

  public AddMissingDeconstructionComponentsFix(@NotNull PsiDeconstructionList deconstructionList,
                                               @NotNull Collection<QuickFixFactory.Pattern> missingPatterns) {
    super(deconstructionList);
    myMissingPatterns = missingPatterns;
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.missing.nested.patterns.fix.text", myMissingPatterns.size() == 1 ? 0 : 1);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    for (var missingPattern : myMissingPatterns) {
      // record pattern is used here to handle not only patterns with explicit types, but also with 'var'
      String text = "o instanceof R(" + missingPattern + ")";
      PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)factory.createExpressionFromText(text, null);
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)instanceOf.getPattern();
      assert deconstructionPattern != null;
      PsiDeconstructionList list = deconstructionPattern.getDeconstructionList();
      PsiPattern component = list.getDeconstructionComponents()[0];
      PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(component);
      assert variable != null;
      startElement.add(component);
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiDeconstructionList copy = (PsiDeconstructionList)PsiTreeUtil.findSameElementInCopy(myStartElement.getElement(), target);
    return copy != null ? new AddMissingDeconstructionComponentsFix(copy, myMissingPatterns) : null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
