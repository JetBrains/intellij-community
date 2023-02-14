// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AddMissingDeconstructionComponentsFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  @SafeFieldForPreview
  private final @NotNull Collection<Pattern> myMissingPatterns;

  public AddMissingDeconstructionComponentsFix(@NotNull PsiDeconstructionList deconstructionList,
                                               @NotNull Collection<Pattern> missingPatterns) {
    super(deconstructionList);
    myMissingPatterns = missingPatterns;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.missing.nested.patterns.fix.text", myMissingPatterns.size());
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiDeconstructionList deconstructionList = (PsiDeconstructionList)startElement;
    if (deconstructionList.getParent() instanceof PsiDeconstructionPattern deconstructionPattern) {
      boolean isEmptyList = deconstructionList.getDeconstructionComponents().length == 0;
      String deconstructionListText = deconstructionList.getText();
      String prefix = deconstructionListText.substring(0, deconstructionListText.length() - 1) + (isEmptyList ? "" : ",");
      String newDeconstructionListText = StreamEx.of(myMissingPatterns).map(Pattern::toString).joining(",", prefix, ")");
      PsiElementFactory factory = PsiElementFactory.getInstance(project);
      String text = "o instanceof " + deconstructionPattern.getTypeElement().getText() + newDeconstructionListText;
      PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)factory.createExpressionFromText(text, null);
      PsiDeconstructionPattern newPattern = (PsiDeconstructionPattern)instanceOf.getPattern();
      assert newPattern != null;
      deconstructionPattern.replace(newPattern);
    }
  }

  public record Pattern(@NotNull String type, @NotNull String name) {
    @Override
    public String toString() {
      return type + " " + name;
    }

    public static Pattern create(@NotNull PsiRecordComponent recordComponent, @NotNull PsiElement context) {
      JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(context.getProject());
      String name = manager.suggestUniqueVariableName(recordComponent.getName(), context, true);
      PsiType type = recordComponent.getType();
      if (type instanceof PsiClassType classType && classType.resolve() instanceof PsiTypeParameter) {
        return new Pattern("var", name);
      }
      return new Pattern(type.getCanonicalText(), name);
    }
  }
}
