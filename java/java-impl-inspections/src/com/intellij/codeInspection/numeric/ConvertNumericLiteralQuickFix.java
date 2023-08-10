// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.numeric;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Converts content of string literals to proposed values. The sole purpose of the class is to act as quickfix
 * for adding/removing underscores to/from numeric literals.
 *
 * @see InsertLiteralUnderscoresInspection
 * @see RemoveLiteralUnderscoresInspection
 */
final class ConvertNumericLiteralQuickFix extends PsiUpdateModCommandQuickFix {

  @NotNull private final String myConvertedValue;
  @NotNull @IntentionName private final String myName;
  @NotNull @IntentionFamilyName private final String myFamilyName;

  ConvertNumericLiteralQuickFix(@NotNull final String convertedValue,
                                @NotNull @IntentionName final String name,
                                @NotNull @IntentionFamilyName String familyName) {
    myConvertedValue = convertedValue;
    myName = name;
    myFamilyName = familyName;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return myFamilyName;
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return myName;
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(myConvertedValue, null);
    element.replace(replacement);
  }
}
